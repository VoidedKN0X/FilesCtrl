@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vknxworks.filesctrl.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.vknxworks.filesctrl.R
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.vknxworks.filesctrl.ui.components.CtrlDot
import com.vknxworks.filesctrl.ui.components.CtrlSectionLabel
import com.vknxworks.filesctrl.ui.components.CtrlSwitch
import com.vknxworks.filesctrl.ui.components.topDivider
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlShapes
import com.vknxworks.filesctrl.ui.theme.CtrlType

// The grouped settings rows every Ctrl app uses (as in MemoCtrl, ProxCtrl and TrueCtrl).

/** A card of related rows with a mono label above it and an optional note below; rows get 1dp dividers. */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    gutter: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 8.dp)) {
        if (title != null) {
            CtrlSectionLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CtrlShapes.Card)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Card),
        ) {
            content()
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = CtrlColors.Text,
    titleColor: Color = CtrlColors.Text,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    below: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            // The first row's line sits under the card's own border, so only lines between rows show.
            .topDivider(color = CtrlColors.CardBorder)
            .then(if (onClick != null && enabled) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(22.dp), tint = iconTint.copy(alpha = alpha * if (iconTint == CtrlColors.Text) 0.85f else 1f))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor.copy(alpha = alpha), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted.copy(alpha = alpha),
                )
            }
            below?.invoke()
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (showChevron) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
        }
    }
}

/** A row with the Ctrl switch. The whole row toggles, and reads as one switch to screen readers. */
@Composable
fun GroupSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    GroupRow(title, subtitle, icon, enabled = enabled, onClick = { onChange(!checked) }, role = Role.Switch) {
        CtrlSwitch(checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** A row showing the current value; tapping opens a sheet to pick another. */
@Composable
fun <T> GroupChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    icon: ImageVector? = null,
    footer: String? = null,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    GroupRow(title, null, icon, onClick = { open = true }, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(current, style = CtrlType.Meta, color = CtrlColors.Muted, maxLines = 1)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
        }
    })
    if (open) {
        ChoiceSheet(title, options, selected, footer, onDismiss = { open = false }) { onPick(it); open = false }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    footer: String? = null,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CtrlColors.Surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        tonalElevation = 0.dp,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(CtrlColors.ControlBorder)) },
        modifier = Modifier.border(1.dp, CtrlColors.CardBorder, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
    ) {
        CtrlSectionLabel(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                val isSel = value == selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).topDivider(inset = 24.dp).clickable { onPick(value) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSel) CtrlColors.AccentText else CtrlColors.Text,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) {
                        val selectedLabel = stringResource(R.string.selected_2)
                        CtrlDot(CtrlColors.AccentText, Modifier.semantics { contentDescription = selectedLabel })
                    }
                }
            }
            if (footer != null) {
                item {
                    Text(
                        footer,
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
