package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.ui.components.CtrlBackBar
import com.vknxworks.filesctrl.ui.components.CtrlSectionLabel
import com.vknxworks.filesctrl.ui.components.CtrlTopBar
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType

// Shared building blocks, kept the same as in the other VKNXWorks Ctrl apps.

/** Room below lists so their last rows can scroll clear of the floating bottom bar. */
val ListBottom = PaddingValues(bottom = 136.dp)

/** Sub screen top bar: square back button and title (the Ctrl / Signal look). */
@Composable
fun BackBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    CtrlBackBar(title, onBack, stringResource(R.string.back), subtitle = subtitle, actions = { actions() })
}

/** Main tab top bar: the FILES/CTRL wordmark, the big page title below and the profile button. */
@Composable
fun TabTitle(title: String, subtitle: String? = null, showProfile: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        CtrlTopBar(
            stringResource(R.string.wordmark_name),
            statusBarPadding = false,
            profile = if (showProfile) { { ProfileButton() } } else null,
        )
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
            if (subtitle != null) Text(subtitle.uppercase(), style = CtrlType.Label, color = CtrlColors.Muted)
            Text(title, style = CtrlType.Headline, color = CtrlColors.Text)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    CtrlSectionLabel(title, modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp), trailing = trailing)
}

/** A titled block of rows, the same as the settings groups in every Ctrl app. */
@Composable
fun SettingsSection(title: String?, modifier: Modifier = Modifier, footer: String? = null, content: @Composable () -> Unit) {
    com.vknxworks.filesctrl.ui.settings.Group(title, modifier, footer) { content() }
}

@Composable
fun itemColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

/** Rows in a group get their 1dp dividers from the group card itself. */
@Composable
fun ItemDivider() = Unit

@Composable
fun NavItem(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    com.vknxworks.filesctrl.ui.settings.GroupRow(title, subtitle, icon, onClick = onClick, below = below, trailing = trailing)
}

@Composable
fun ToggleItem(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    com.vknxworks.filesctrl.ui.settings.GroupSwitch(title, subtitle, checked, icon, enabled, onChange)
}

/** A row showing the current choice; tapping it opens a list to pick another. */
@Composable
fun ChoiceItem(icon: ImageVector?, title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    com.vknxworks.filesctrl.ui.settings.GroupChoice(title, options.mapIndexed { i, label -> i to label }, selected, icon) { onSelect(it) }
}

@Composable
fun EmptyBox(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = CtrlColors.Inactive)
        Spacer(Modifier.height(12.dp))
        Text(text, textAlign = TextAlign.Center, color = CtrlColors.Muted)
    }
}
