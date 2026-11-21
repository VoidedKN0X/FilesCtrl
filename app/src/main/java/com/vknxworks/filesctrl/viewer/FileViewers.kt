package com.vknxworks.filesctrl.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Mime
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.ui.Fmt
import com.vknxworks.filesctrl.ui.components.CtrlBackBar
import com.vknxworks.filesctrl.ui.components.CtrlSquareButton
import com.vknxworks.filesctrl.ui.components.CtrlTextField
import com.vknxworks.filesctrl.ui.contentUri
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer

/** Which built-in viewer a file opens in, besides pictures (and the text editor). */
object FileViewers {
    fun isPdf(e: FileEntry): Boolean = !e.isDir && (e.ext == "pdf" || e.mime == "application/pdf")
    fun isAudio(e: FileEntry): Boolean = !e.isDir && Mime.kind(e) == Mime.Kind.Audio

    fun pdfRoute(e: FileEntry) = "pdf?loc=" + android.net.Uri.encode(e.location.key)
    fun audioRoute(e: FileEntry) = "audio?loc=" + android.net.Uri.encode(e.location.key)
    fun hexRoute(e: FileEntry) = "hex?loc=" + android.net.Uri.encode(e.location.key)
}

/**
 * Opens [e] for reading through FilesCtrl's own document provider (or the phone's file), the same way other apps get
 * it: files on servers, in archives and in vaults are read on demand with seeking, never copied to the phone first.
 */
private fun openFd(ctx: Context, e: FileEntry): ParcelFileDescriptor =
    ctx.contentResolver.openFileDescriptor(contentUri(ctx, e), "r") ?: throw java.io.IOException(App.str(R.string.couldn_t_open, e.name))

/** Looks up [loc] (the viewer may be opened again after Android restarted the app). */
@Composable
private fun rememberEntry(loc: Location): Pair<FileEntry?, String?> {
    val result by produceState<Pair<FileEntry?, String?>>(null to null, loc) {
        value = try {
            withContext(Dispatchers.IO) { App.vfs.get(loc.vfsId).stat(loc.path) } to null
        } catch (e: Exception) {
            null to (e.message ?: e.javaClass.simpleName)
        }
    }
    return result
}

@Composable
private fun ViewerFrame(title: String, subtitle: String?, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        CtrlBackBar(title, onBack = onBack, backDescription = stringResource(R.string.back), subtitle = subtitle)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun ViewerError(name: String, message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.couldn_t_open, name), style = MaterialTheme.typography.titleMedium, color = CtrlColors.Text)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
    }
}

// ---------- PDF ----------

/**
 * PDF viewer with Android's own PDF renderer: pages one under the other, pinch to zoom, page number. Pages are drawn
 * when they come into view and kept in memory only. Password-protected PDFs open in another app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun PdfViewerScreen(loc: Location, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val (entry, statError) = rememberEntry(loc)
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // PdfRenderer allows one page open at a time and isn't thread-safe: everything goes through one thread.
    val pdfThread = remember { Dispatchers.IO.limitedParallelism(1) }
    val lock = remember { Mutex() }
    val pages = remember { object : LruCache<Int, Bitmap>(48 * 1024 * 1024) { override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount } }

    LaunchedEffect(entry) {
        val e = entry ?: return@LaunchedEffect
        try {
            renderer = withContext(pdfThread) { PdfRenderer(openFd(ctx, e)) }
        } catch (ex: SecurityException) {
            error = App.str(R.string.pdf_protected)
        } catch (ex: Exception) {
            error = ex.message ?: ex.javaClass.simpleName
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val r = renderer
            renderer = null
            pages.evictAll()
            // Closed on the PDF thread, after any page that's being drawn.
            App.scope.launch(pdfThread) { lock.withLock { runCatching { r?.close() } } }
        }
    }

    val name = entry?.name ?: Paths.name(loc.path)
    val r = renderer
    val listState = rememberLazyListState()
    val pageNow by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    ViewerFrame(name, r?.let { stringResource(R.string.pdf_page_of, pageNow, it.pageCount) }, onBack) {
        when {
            statError != null -> ViewerError(name, statError)
            error != null -> ViewerError(name, error ?: "")
            r == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = CtrlColors.Accent)
            else -> {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val zoom = rememberTransformableState { z, pan, _ ->
                    scale = (scale * z).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
                BoxWithConstraints(
                    Modifier.fillMaxSize().background(CtrlColors.Surface)
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; if (scale == 1f) offset = Offset.Zero }) }
                        .transformable(zoom, canPan = { scale > 1f }),
                ) {
                    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                    LazyColumn(
                        Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(r.pageCount) { i ->
                            val aspect = remember(i) { mutableFloatStateOf(1f / 1.414f) }
                            val bmp by produceState<Bitmap?>(pages.get(i), i, r) {
                                if (value != null) return@produceState
                                value = withContext(pdfThread) {
                                    lock.withLock {
                                        if (renderer !== r) return@withLock null
                                        runCatching {
                                            r.openPage(i).use { page ->
                                                aspect.floatValue = page.width.toFloat() / page.height.coerceAtLeast(1)
                                                // Twice the screen width, so zooming in stays readable.
                                                val w = (widthPx * 2).coerceAtMost(4096)
                                                val h = (w * page.height.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 8192)
                                                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                                b.eraseColor(android.graphics.Color.WHITE)
                                                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                b
                                            }
                                        }.getOrNull()
                                    }
                                }?.also { pages.put(i, it) }
                            }
                            val b = bmp
                            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).aspectRatio(b?.let { it.width.toFloat() / it.height } ?: aspect.floatValue).background(Color.White)) {
                                if (b != null) Image(b.asImageBitmap(), stringResource(R.string.pdf_page, i + 1), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Hex ----------

private const val ROW = 16
private const val CHUNK = 4096

/**
 * Hex viewer: offset, 16 bytes in hex and the same bytes as text, for any file of any size. Bytes are read in
 * 4 KB pieces as you scroll; "Go to" jumps to an offset. Read-only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun HexViewerScreen(loc: Location, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val (entry, statError) = rememberEntry(loc)
    var fd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val reader = remember { Dispatchers.IO.limitedParallelism(1) }
    val chunks = remember { LruCache<Long, ByteArray>(256) }
    var loaded by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var goTo by remember { mutableStateOf("") }

    LaunchedEffect(entry) {
        val e = entry ?: return@LaunchedEffect
        try { fd = withContext(Dispatchers.IO) { openFd(ctx, e) } } catch (ex: Exception) { error = ex.message ?: ex.javaClass.simpleName }
    }
    DisposableEffect(Unit) { onDispose { runCatching { fd?.close() } } }

    val size = entry?.size ?: 0L
    val rows = ((size + ROW - 1) / ROW).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    fun bytesAt(chunk: Long): ByteArray? = chunks.get(chunk)

    suspend fun load(chunk: Long) {
        if (chunks.get(chunk) != null) return
        val f = fd ?: return
        val data = withContext(reader) {
            runCatching {
                val ch = FileInputStream(f.fileDescriptor).channel
                val buf = ByteBuffer.allocate(CHUNK)
                var pos = chunk * CHUNK
                while (buf.hasRemaining()) {
                    val n = ch.read(buf, pos)
                    if (n <= 0) break
                    pos += n
                }
                buf.flip()
                ByteArray(buf.remaining()).also { buf.get(it) }
            }.getOrNull()
        } ?: return
        chunks.put(chunk, data)
        loaded++
    }

    val name = entry?.name ?: Paths.name(loc.path)
    ViewerFrame(name, entry?.let { "${Fmt.size(ctx, it.size)} · ${App.plural(R.plurals.hex_bytes, it.size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), it.size)}" }, onBack) {
        when {
            statError != null -> ViewerError(name, statError)
            error != null -> ViewerError(name, error ?: "")
            entry == null || fd == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = CtrlColors.Accent)
            size == 0L -> ViewerError(name, stringResource(R.string.hex_empty))
            else -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CtrlTextField(
                        goTo, { v -> goTo = v.filter { it.isLetterOrDigit() || it == 'x' }.take(18) },
                        label = { Text(stringResource(R.string.hex_go_to)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            parseOffset(goTo)?.let { off -> scope.launch { listState.scrollToItem((off.coerceIn(0, size - 1) / ROW).toInt()) } }
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { parseOffset(goTo)?.let { off -> scope.launch { listState.scrollToItem((off.coerceIn(0, size - 1) / ROW).toInt()) } } }) {
                        Text(stringResource(R.string.hex_go), color = CtrlColors.AccentText)
                    }
                }
                Text(stringResource(R.string.hex_go_to_hint), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(6.dp))
                val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
                        items(rows) { row ->
                            val start = row.toLong() * ROW
                            val chunk = start / CHUNK
                            LaunchedEffect(chunk) { load(chunk) }
                            // Read again once a piece has been loaded.
                            val data = if (loaded >= 0) bytesAt(chunk) else null
                            Text(hexLine(start, data, ((start % CHUNK).toInt()), size), style = mono, color = CtrlColors.Text, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/** "0x1F", "1f" (hex) or "4096" (decimal) as an offset; null when it isn't a number. */
internal fun parseOffset(text: String): Long? {
    val t = text.trim().lowercase()
    if (t.isEmpty()) return null
    return if (t.startsWith("0x")) t.drop(2).toLongOrNull(16)
    else if (t.any { it in 'a'..'f' }) t.toLongOrNull(16)
    else t.toLongOrNull()
}

/** One row: offset, 16 bytes in hex, and as text (dots for anything that isn't printable ASCII). */
internal fun hexLine(start: Long, chunk: ByteArray?, at: Int, size: Long): String {
    val sb = StringBuilder(80)
    sb.append("%08X".format(start)).append("  ")
    val n = minOf(ROW.toLong(), size - start).toInt()
    val text = StringBuilder(ROW)
    for (i in 0 until ROW) {
        if (i == 8) sb.append(' ')
        if (i < n && chunk != null && at + i < chunk.size) {
            val b = chunk[at + i].toInt() and 0xFF
            sb.append(HEX[b ushr 4]).append(HEX[b and 0xF]).append(' ')
            text.append(if (b in 0x20..0x7E) b.toChar() else '.')
        } else {
            sb.append(if (i < n) ".. " else "   ")
        }
    }
    return sb.append(' ').append(text).toString()
}

private val HEX = "0123456789ABCDEF".toCharArray()

// ---------- Audio ----------

/**
 * Audio player for one file: play and pause, ten seconds back and forward, a position bar, and the title, artist and
 * album the file names. Plays while this screen is open: it stops when you leave the screen and pauses when you leave
 * the app (so it never plays on behind the app lock).
 */
@Composable
fun AudioPlayerScreen(loc: Location, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val (entry, statError) = rememberEntry(loc)
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val tags by produceState<Triple<String?, String?, String?>>(Triple(null, null, null), entry) {
        val e = entry ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, contentUri(ctx, e))
                    Triple(
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    )
                } finally {
                    r.release()
                }
            }.getOrDefault(Triple(null, null, null))
        }
    }

    DisposableEffect(entry) {
        val e = entry ?: return@DisposableEffect onDispose { }
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            mp.setDataSource(ctx, contentUri(ctx, e))
            mp.setOnPreparedListener { duration = it.duration.coerceAtLeast(0); it.start(); playing = true; player = it }
            mp.setOnCompletionListener { playing = false; position = duration }
            mp.setOnErrorListener { _, what, extra -> error = App.str(R.string.audio_cant_play, "$what/$extra"); playing = false; true }
            mp.prepareAsync()
        } catch (ex: Exception) {
            error = ex.message ?: ex.javaClass.simpleName
        }
        onDispose {
            player = null
            runCatching { mp.stop() }
            mp.release()
        }
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        player?.let { if (playing) { runCatching { it.pause() }; playing = false } }
    }
    LaunchedEffect(player, playing) {
        while (player != null && playing) {
            position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            delay(250)
        }
    }

    val name = entry?.name ?: Paths.name(loc.path)
    ViewerFrame(name, entry?.let { Fmt.size(ctx, it.size) }, onBack) {
        when {
            statError != null -> ViewerError(name, statError)
            error != null -> ViewerError(name, error ?: "")
            else -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(120.dp).background(CtrlColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MusicNote, null, Modifier.size(56.dp), tint = CtrlColors.AccentText)
                }
                Spacer(Modifier.height(24.dp))
                Text(tags.first ?: name, style = MaterialTheme.typography.titleLarge, color = CtrlColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                listOfNotNull(tags.second, tags.third).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(24.dp))
                val mp = player
                Slider(
                    value = dragging ?: if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        val f = dragging
                        dragging = null
                        if (f != null && mp != null && duration > 0) { val to = (f * duration).toInt(); mp.seekTo(to); position = to }
                    },
                    enabled = mp != null && duration > 0,
                    colors = SliderDefaults.colors(thumbColor = CtrlColors.Accent, activeTrackColor = CtrlColors.Accent, inactiveTrackColor = CtrlColors.ProgressTrack),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(time(dragging?.let { (it * duration).toInt() } ?: position), style = CtrlType.Label, color = CtrlColors.Muted, modifier = Modifier.weight(1f))
                    Text(time(duration), style = CtrlType.Label, color = CtrlColors.Muted)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CtrlSquareButton(Icons.Outlined.Replay10, stringResource(R.string.audio_back_10), {
                        mp?.let { val to = (it.currentPosition - 10_000).coerceAtLeast(0); it.seekTo(to); position = to }
                    })
                    if (mp == null) CircularProgressIndicator(Modifier.size(44.dp), color = CtrlColors.Accent)
                    else CtrlSquareButton(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        stringResource(if (playing) R.string.audio_pause else R.string.audio_play),
                        {
                            if (playing) { mp.pause(); playing = false }
                            else { if (position >= duration && duration > 0) mp.seekTo(0); mp.start(); playing = true }
                        },
                    )
                    CtrlSquareButton(Icons.Outlined.Forward10, stringResource(R.string.audio_forward_10), {
                        mp?.let { val to = (it.currentPosition + 10_000).coerceAtMost(duration); it.seekTo(to); position = to }
                    })
                }
            }
        }
    }
}

private fun time(ms: Int): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
