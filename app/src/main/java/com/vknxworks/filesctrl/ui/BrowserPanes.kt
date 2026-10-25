package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlShapes
import com.vknxworks.filesctrl.ui.theme.CtrlType

/**
 * The Files tab: one browser with tabs, or on wide windows two browsers side by side, each with its own tabs.
 * Touching a pane makes it the one Places, shortcuts and back act on.
 */
@Composable
fun BrowserPanes(dual: Boolean, islandSpace: Dp) {
    BrowserTabs.init()
    LaunchedEffect(dual) { if (dual) BrowserTabs.onDualPaneShown() }
    if (!dual) {
        PaneView(BrowserTabs.left, dual = false, islandSpace = islandSpace, modifier = Modifier.fillMaxSize())
        return
    }
    Row(Modifier.fillMaxSize()) {
        PaneView(BrowserTabs.left, dual = true, islandSpace = islandSpace, modifier = Modifier.weight(1f).fillMaxHeight())
        Box(Modifier.width(1.dp).fillMaxHeight().background(CtrlColors.CardBorder))
        PaneView(BrowserTabs.right, dual = true, islandSpace = islandSpace, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun PaneView(pane: Pane, dual: Boolean, islandSpace: Dp, modifier: Modifier) {
    val vm = pane.vm
    val focused = !dual || BrowserTabs.focusedRight == pane.right
    Box(
        modifier.pointerInput(pane.right) {
            // Any touch in a pane focuses it, without taking the touch from the list or buttons underneath.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                BrowserTabs.focusedRight = pane.right
            }
        },
    ) {
        // Each tab keeps its own scroll position, selection and dialogs.
        key(vm) {
            BrowserScreen(
                vm, islandSpace,
                pane = pane, dual = dual, focused = focused,
            )
        }
    }
}

/** The tabs of one pane, under the top bar: tap to switch, × to close, + for a new tab of the folder you're in. */
@Composable
fun TabStrip(pane: Pane, focused: Boolean, dual: Boolean) {
    val state = rememberLazyListState()
    LaunchedEffect(pane.active, pane.tabs.size) { if (pane.tabs.isNotEmpty()) state.animateScrollToItem(pane.active.coerceIn(0, pane.tabs.size - 1)) }
    Box(Modifier.fillMaxWidth()) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            itemsIndexed(pane.tabs, key = { _, t -> t.tabId }) { i, t ->
                val selected = i == pane.active
                val shape = CtrlShapes.Badge
                Row(
                    Modifier
                        .padding(end = 6.dp)
                        .widthIn(max = 200.dp)
                        .clip(shape)
                        .background(if (selected) (if (focused) CtrlColors.Accent else CtrlColors.Raised) else CtrlColors.Surface)
                        .border(1.dp, if (selected) androidx.compose.ui.graphics.Color.Transparent else CtrlColors.ControlBorder, shape)
                        .clickable { pane.active = i; BrowserTabs.focusedRight = pane.right }
                        .padding(start = 12.dp, end = if (pane.tabs.size > 1) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        t.title.ifEmpty { "/" },
                        style = CtrlType.Meta.copy(fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (selected && focused) CtrlColors.Ink else CtrlColors.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (pane.tabs.size > 1) {
                        Spacer(Modifier.width(2.dp))
                        Box(
                            Modifier.size(24.dp).clip(CtrlShapes.Badge).clickable { pane.close(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Close, stringResource(R.string.close_tab, t.title), Modifier.size(14.dp),
                                tint = if (selected && focused) CtrlColors.Ink else CtrlColors.Muted,
                            )
                        }
                    }
                }
            }
            item {
                if (pane.tabs.size < BrowserTabs.MAX_TABS) Box(
                    Modifier.size(32.dp).clip(CtrlShapes.Badge).border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Badge)
                        .clickable { BrowserTabs.openTab(pane.vm, pane.vm.location); BrowserTabs.focusedRight = pane.right },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Add, stringResource(R.string.new_tab), Modifier.size(18.dp), tint = CtrlColors.AccentText) }
            }
        }
    }
    if (dual) Box(Modifier.fillMaxWidth().height(2.dp).background(if (focused) CtrlColors.AccentText else androidx.compose.ui.graphics.Color.Transparent))
}
