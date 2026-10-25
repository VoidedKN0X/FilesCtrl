package com.vknxworks.filesctrl.viewer

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.fs.ArchiveVfs
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.ops.Ops
import com.vknxworks.filesctrl.signalBarStyle
import com.vknxworks.filesctrl.ui.BrowserViewModel
import com.vknxworks.filesctrl.ui.DeleteDialog
import com.vknxworks.filesctrl.ui.Fmt
import com.vknxworks.filesctrl.ui.displayPath
import com.vknxworks.filesctrl.ui.isAppInDarkTheme
import com.vknxworks.filesctrl.ui.isReadOnly
import com.vknxworks.filesctrl.ui.openFile
import com.vknxworks.filesctrl.ui.shareFiles
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** Pictures the built-in viewer opens. SVG and other vector or raw formats still go to another app. */
object Viewable {
    private val ext = setOf("jpg", "jpeg", "jpe", "jfif", "png", "webp", "gif", "heic", "heif", "avif", "bmp")
    fun isImage(e: FileEntry): Boolean = !e.isDir && e.ext in ext
}

/** The pictures of the folder the viewer was opened from, handed over by the browser. */
object ViewerSession {
    var images: List<FileEntry> = emptyList()
        private set
    fun set(list: List<FileEntry>) { images = list.filter { Viewable.isImage(it) } }
}

fun viewerRoute(e: FileEntry) = "view?loc=" + android.net.Uri.encode(e.location.key)

/**
 * Full-screen picture viewer: swipe between the pictures of the folder, pinch or double-tap to zoom, rotate the
 * view, the info sheet (size, dimensions, date and camera from EXIF), and share, delete or open with another app.
 * Pictures on servers are read through FilesCtrl's own file systems and cached ([ImageCache]).
 */
@Composable
fun ImageViewerScreen(start: Location, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val images = remember { mutableStateListOf<FileEntry>() }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(start) {
        val handed = ViewerSession.images
        if (handed.any { it.location == start }) {
            images.addAll(handed)
        } else {
            // Opened afresh (e.g. after Android restarted the app): list the folder again.
            try {
                val list = withContext(Dispatchers.IO) {
                    val vfs = App.vfs.get(start.vfsId)
                    vfs.list(Paths.parent(start.path)).filter { Viewable.isImage(it) && (App.settings.showHidden || !it.hidden) }
                }
                val s = App.settings
                images.addAll(list.sortedWith(BrowserViewModel.comparator(s.sortBy, s.sortDescending, s.foldersFirst)))
                if (images.none { it.location == start }) {
                    withContext(Dispatchers.IO) { App.vfs.get(start.vfsId).stat(start.path) }.let { images.add(0, it) }
                }
            } catch (e: Exception) {
                failed = e.message ?: e.javaClass.simpleName
            }
        }
        ready = true
    }

    // Light status and navigation bar icons on the black background, whatever the theme; back to normal after.
    val dark = isAppInDarkTheme()
    DisposableEffect(Unit) {
        val act = ctx as? ComponentActivity
        act?.enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.BLACK), SystemBarStyle.dark(android.graphics.Color.BLACK))
        onDispose { act?.enableEdgeToEdge(signalBarStyle(dark), signalBarStyle(dark)) }
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            failed != null -> Message(stringResource(R.string.couldn_t_open, Paths.name(start.path)), failed)
            !ready -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = CtrlColors.Accent)
            images.isEmpty() -> { LaunchedEffect(Unit) { onBack() } }
            else -> Pages(images, images.indexOfFirst { it.location == start }.coerceAtLeast(0), onBack)
        }
    }
}

@Composable
private fun Pages(images: MutableList<FileEntry>, initial: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = initial) { images.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val rotation = remember { mutableStateMapOf<String, Int>() }
    var info by remember { mutableStateOf<FileEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<FileEntry?>(null) }
    val current = images.getOrNull(pager.currentPage)

    // A zoomed picture keeps the swipe for panning; reset when moving on.
    LaunchedEffect(pager.currentPage) { zoomed = false }

    HorizontalPager(
        state = pager,
        userScrollEnabled = !zoomed,
        beyondViewportPageCount = 1,
        key = { images.getOrNull(it)?.path ?: "page-$it" },
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val e = images.getOrNull(page) ?: return@HorizontalPager
        ZoomableImage(
            e,
            rotation = rotation[e.path] ?: 0,
            onTap = { chrome = !chrome },
            onZoomed = { if (page == pager.currentPage) zoomed = it },
        )
    }

    AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(current?.name.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (images.size > 1) Text(
                        stringResource(R.string.viewer_position, pager.currentPage + 1, images.size),
                        color = Color.White.copy(alpha = 0.7f), style = CtrlType.Meta,
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val e = current
                IconButton(onClick = { e?.let { rotation[it.path] = ((rotation[it.path] ?: 0) + 90) % 360 } }) {
                    Icon(Icons.AutoMirrored.Outlined.RotateRight, stringResource(R.string.viewer_rotate), tint = Color.White)
                }
                IconButton(onClick = { info = e }) { Icon(Icons.Outlined.Info, stringResource(R.string.viewer_info), tint = Color.White) }
                IconButton(onClick = { e?.let { shareFiles(ctx, listOf(it)) } }) { Icon(Icons.Outlined.Share, stringResource(R.string.share), tint = Color.White) }
                IconButton(onClick = { e?.let { openFile(ctx, it, chooser = true) } }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.open_with), tint = Color.White)
                }
                if (e != null && !isReadOnly(e) && !e.vfsId.startsWith(ArchiveVfs.PREFIX)) {
                    IconButton(onClick = {
                        if (App.settings.confirmDelete) confirmDelete = e else { Ops.delete(listOf(e)); images.remove(e); if (images.isEmpty()) onBack() }
                    }) { Icon(Icons.Outlined.Delete, stringResource(R.string.lock_delete), tint = Color.White) }
                }
            }
        }
    }

    info?.let { e -> InfoSheet(e) { info = null } }
    confirmDelete?.let { e ->
        DeleteDialog(listOf(e), remote = e.vfsId.startsWith(com.vknxworks.filesctrl.fs.RemoteVfs.PREFIX), onDismiss = { confirmDelete = null }) {
            confirmDelete = null
            Ops.delete(listOf(e))
            images.remove(e)
            if (images.isEmpty()) onBack()
        }
    }
}

@Composable
private fun ZoomableImage(e: FileEntry, rotation: Int, onTap: () -> Unit, onZoomed: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var scale by remember(e.path) { mutableFloatStateOf(1f) }
    var offset by remember(e.path) { mutableStateOf(Offset.Zero) }
    var error by remember(e.path) { mutableStateOf(false) }
    var loading by remember(e.path) { mutableStateOf(true) }
    // Decoded at up to twice the screen size (at most 4096px), sharp when zoomed without huge photos running out of memory.
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val maxPx = with(density) { (maxOf(cfg.screenWidthDp, cfg.screenHeightDp).dp.toPx() * 2).toInt() }.coerceIn(1024, 4096)
    val request = remember(e.path, e.modified, maxPx) {
        ImageRequest.Builder(ctx).data(VfsImage(e)).size(maxPx, maxPx).precision(Precision.INEXACT).build()
    }
    LaunchedEffect(rotation) { scale = 1f; offset = Offset.Zero; onZoomed(false) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(e.path) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        onZoomed(scale > 1f)
                    },
                )
            }
            .pointerInput(e.path) {
                // Pinch to zoom, and drag while zoomed in. One finger at normal size is left to the pager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 8f)
                            offset = if (scale <= 1f) Offset.Zero else {
                                val o = offset + event.calculatePan()
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                            }
                            onZoomed(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (error) {
            Message(stringResource(R.string.viewer_cant_show), stringResource(R.string.viewer_cant_show_body)) {
                TextButton(onClick = { openFile(ctx, e, chooser = true) }) { Text(stringResource(R.string.open_with), color = CtrlColors.Accent) }
            }
        } else {
            AsyncImage(
                model = request,
                contentDescription = e.name,
                contentScale = ContentScale.Fit,
                onSuccess = { loading = false },
                onError = { loading = false; error = true },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    rotationZ = rotation.toFloat()
                },
            )
            if (loading) CircularProgressIndicator(color = CtrlColors.Accent)
        }
    }
}

@Composable
private fun Message(title: String, body: String?, action: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.BrokenImage, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(12.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        action()
    }
}

/** What the info sheet shows; read off the main thread from the picture (cached for server files). */
private data class ImageInfo(
    val width: Int?, val height: Int?, val taken: String?, val camera: String?, val lens: String?,
    val exposure: String?, val size: Long,
)

private fun readInfo(e: FileEntry, file: java.io.File): ImageInfo {
    var w: Int? = null
    var h: Int? = null
    try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth > 0 && o.outHeight > 0) { w = o.outWidth; h = o.outHeight }
    } catch (_: Exception) {}
    var taken: String? = null
    var camera: String? = null
    var lens: String? = null
    var exposure: String? = null
    try {
        val x = ExifInterface(file.path)
        if (w == null) {
            val ew = x.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val eh = x.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            if (ew > 0 && eh > 0) { w = ew; h = eh }
        }
        // Pictures stored sideways show their real (upright) size.
        val orientation = x.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        if (orientation in setOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE)) {
            val t = w; w = h; h = t
        }
        val dt = x.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: x.getAttribute(ExifInterface.TAG_DATETIME)
        taken = dt?.let { raw ->
            runCatching {
                val d = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).parse(raw)
                d?.let { Fmt.dateTime(App.context, it.time) }
            }.getOrNull() ?: raw
        }
        val make = x.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
        val model = x.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
        camera = when {
            model.isEmpty() -> make.ifEmpty { null }
            make.isEmpty() || model.startsWith(make, ignoreCase = true) -> model
            else -> "$make $model"
        }
        lens = x.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifEmpty { null }
        val parts = mutableListOf<String>()
        x.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()?.let { parts += "f/" + "%.1f".format(Locale.ROOT, it) }
        x.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.takeIf { it > 0 }?.let {
            parts += if (it < 1.0) "1/" + Math.round(1.0 / it) + "s" else "%.1fs".format(Locale.ROOT, it)
        }
        x.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { raw ->
            val v = raw.split('/').let { p -> if (p.size == 2) p[0].toDoubleOrNull()?.div(p[1].toDoubleOrNull() ?: 1.0) else raw.toDoubleOrNull() }
            v?.takeIf { it > 0 }?.let { parts += "%.0fmm".format(Locale.ROOT, it) }
        }
        x.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { parts += "ISO $it" }
        exposure = parts.joinToString("  ").ifEmpty { null }
    } catch (_: Exception) {}
    return ImageInfo(w, h, taken, camera, lens, exposure, if (e.size > 0) e.size else file.length())
}

@Composable
private fun InfoSheet(e: FileEntry, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var info by remember(e.path) { mutableStateOf<ImageInfo?>(null) }
    var err by remember(e.path) { mutableStateOf<String?>(null) }
    LaunchedEffect(e.path) {
        try {
            info = withContext(Dispatchers.IO) { readInfo(e, ImageCache.file(e)) }
        } catch (ex: kotlinx.coroutines.CancellationException) {
            throw ex
        } catch (ex: Exception) {
            err = ex.message ?: ex.javaClass.simpleName
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CtrlColors.Surface, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(e.name, style = CtrlType.Title, color = CtrlColors.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            val i = info
            InfoRow(stringResource(R.string.location), if (e.vfsId.startsWith(ArchiveVfs.PREFIX)) displayPath(e) else Paths.parent(displayPath(e)))
            InfoRow(stringResource(R.string.size), Fmt.size(ctx, i?.size ?: e.size))
            val w = i?.width
            val h = i?.height
            if (w != null && h != null) {
                val mp = w.toLong() * h / 1_000_000.0
                InfoRow(stringResource(R.string.viewer_dimensions), stringResource(R.string.viewer_dimensions_value, w, h, "%.1f".format(mp)))
            }
            InfoRow(stringResource(R.string.modified), Fmt.dateTime(ctx, e.modified))
            i?.taken?.let { InfoRow(stringResource(R.string.viewer_taken), it) }
            i?.camera?.let { InfoRow(stringResource(R.string.viewer_camera), it) }
            i?.lens?.let { InfoRow(stringResource(R.string.viewer_lens), it) }
            i?.exposure?.let { InfoRow(stringResource(R.string.viewer_exposure), it) }
            if (i == null && err == null) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(20.dp), color = CtrlColors.AccentText, strokeWidth = 2.dp)
            err?.let { Text(it, color = CtrlColors.Error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label.uppercase(), style = CtrlType.Label, color = CtrlColors.Muted)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text) }
    }
}
