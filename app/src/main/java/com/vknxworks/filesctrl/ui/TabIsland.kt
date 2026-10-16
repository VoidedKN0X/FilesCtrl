package com.vknxworks.filesctrl.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

data class IslandTab(val index: Int, val icon: ImageVector, val label: String, val badge: Int = 0)

private val IslandShape = RoundedCornerShape(32.dp)

/**
 * Floating tab bar: a frosted pill that blurs whatever scrolls behind it, tinted with the theme's surface
 * colour, with a selection bubble that springs between tabs.
 */
@Composable
fun TabIsland(
    tabs: List<IslandTab>,
    selected: Int,
    hazeState: HazeState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dark = isAppInDarkTheme()
    val haptics = LocalHapticFeedback.current
    val glass = HazeStyle(
        backgroundColor = cs.surface,
        tints = listOf(HazeTint(cs.surfaceContainer.copy(alpha = if (dark) 0.55f else 0.62f))),
        blurRadius = 26.dp,
        noiseFactor = 0.04f,
    )

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BoxWithConstraints(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(66.dp)
                .shadow(14.dp, IslandShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(IslandShape)
                .hazeEffect(state = hazeState, style = glass)
                .border(
                    1.dp,
                    // A light rim along the top edge, like light catching glass.
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.7f), cs.outlineVariant.copy(alpha = 0.25f))
                    ),
                    IslandShape,
                ),
        ) {
            val pos = tabs.indexOfFirst { it.index == selected }.coerceAtLeast(0)
            val itemWidth = maxWidth / tabs.size.coerceAtLeast(1)
            val bubbleX by animateDpAsState(
                itemWidth * pos,
                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                label = "bubble",
            )
            // Selection bubble.
            Box(
                Modifier
                    .offset(x = bubbleX)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(5.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(cs.secondaryContainer.copy(alpha = if (dark) 0.85f else 0.9f)),
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { t ->
                    val isSel = t.index == selected
                    val tint by animateColorAsState(if (isSel) cs.onSecondaryContainer else cs.onSurfaceVariant, label = "tint")
                    val src = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressScale(src, 0.88f)
                            .clickable(src, indication = null) {
                                if (!isSel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(t.index)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BadgedBox(badge = {
                            if (t.badge > 0) Badge { Text(if (t.badge > 99) "99+" else "${t.badge}") }
                        }) {
                            Icon(t.icon, t.label, Modifier.size(24.dp), tint = tint)
                        }
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                            color = tint, maxLines = 1, overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

