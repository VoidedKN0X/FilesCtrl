package com.vknxworks.filesctrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.ui.pressScale
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlFonts
import com.vknxworks.filesctrl.ui.theme.CtrlShapes
import com.vknxworks.filesctrl.ui.theme.CtrlType

// The "Ctrl / Signal" building blocks shared by every screen: top bars, cards, rows, switches, buttons,
// fields, dialogs and the floating bottom bar.

/** "FILES/CTRL" with the slash in the accent. */
@Composable
fun CtrlWordmark(name: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(name.uppercase())
            withStyle(SpanStyle(color = CtrlColors.Accent)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Mono, upper case section label ("PRIVACY", "NETWORK"). */
@Composable
fun CtrlSectionLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = CtrlType.Label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 44dp rounded-square button with a 1dp border: back, close and other bar buttons. */
@Composable
fun CtrlSquareButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(44.dp)
            .pressScale(src, 0.92f)
            .clip(CtrlShapes.Control)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Control)
            .clickable(src, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = if (enabled) CtrlColors.Text else CtrlColors.Inactive)
    }
}

/** A plain 44dp icon button for top bar actions. */
@Composable
fun CtrlIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CtrlColors.Text,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint, disabledContentColor = CtrlColors.Inactive),
    ) { Icon(icon, contentDescription, Modifier.size(22.dp)) }
}

/**
 * The 44dp profile button at the top right: surface fill, 1dp border, and a small accent square when
 * connected. [content] is the picture or initials.
 */
@Composable
fun CtrlAvatarButton(
    onClick: () -> Unit,
    contentDescription: String?,
    connected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    Box(Modifier.size(44.dp).pressScale(src, 0.92f)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShapes.Control)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Control)
                .clickable(src, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
                .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
            contentAlignment = Alignment.Center,
        ) { content() }
        if (connected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(3.dp, 3.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CtrlColors.Background)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CtrlColors.Accent),
            )
        }
    }
}

/**
 * Main screen top bar: wordmark on the left, [actions] and the profile button on the right. No colour fill.
 * [statusBarPadding]: false when the screen already sits below the status bar.
 */
@Composable
fun CtrlTopBar(
    name: String,
    modifier: Modifier = Modifier,
    statusBarPadding: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    profile: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (statusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .padding(start = 20.dp, end = 16.dp, top = 12.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlWordmark(name)
        Spacer(Modifier.weight(1f))
        actions()
        if (profile != null) {
            Spacer(Modifier.width(6.dp))
            profile()
        }
    }
}

/** Sub screen top bar: a 44dp square back button, the title in Space Grotesk, and [actions]. */
@Composable
fun CtrlBackBar(
    title: String,
    onBack: () -> Unit,
    backDescription: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlSquareButton(backIcon, backDescription, onBack)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = CtrlType.Title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).sp),
                color = CtrlColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = CtrlType.Meta, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/** Secondary card: surface fill, 1dp border, 22dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = CtrlColors.CardBorder,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CtrlShapes.Card)
            .background(CtrlColors.Surface)
            .border(1.dp, borderColor, CtrlShapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** The one most important thing on a main screen: accent fill, dark content, mono label row, big number. */
@Composable
fun CtrlHeroCard(
    label: String,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(src, 0.98f)
            .clip(CtrlShapes.Hero)
            .background(CtrlColors.Accent)
            .then(if (onClick != null) Modifier.clickable(src, indication = androidx.compose.material3.ripple(color = CtrlColors.Ink), onClick = onClick) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = CtrlType.LabelStrong, color = CtrlColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (trailingLabel != null) Text(trailingLabel.uppercase(), style = CtrlType.LabelStrong.copy(letterSpacing = 0.sp), color = CtrlColors.Ink, maxLines = 1)
        }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides CtrlColors.Ink) {
            content()
        }
    }
}

/** 8dp status square with 2dp corners. */
@Composable
fun CtrlDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CtrlShapes.Dot).background(color))
}

/** Mono status text with its dot: accent while active, grey otherwise. */
@Composable
fun CtrlStatus(text: String, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) CtrlColors.Accent else CtrlColors.Inactive
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = CtrlType.Label.copy(letterSpacing = 0.1.em), color = color, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        CtrlDot(color)
    }
}

/** Mono protocol or ID chip with a thin border ("NFS", "SMB"). */
@Composable
fun CtrlChip(text: String, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .heightIn(min = 28.dp)
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(9.dp))
            .then(
                if (filled) Modifier.background(CtrlColors.Accent)
                else Modifier.border(1.dp, CtrlColors.PinEmpty, RoundedCornerShape(9.dp))
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = CtrlFonts.Mono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
            color = if (filled) CtrlColors.Ink else CtrlColors.Text,
            maxLines = 1,
        )
    }
}

/** 6dp progress bar with 3dp corners. */
@Composable
fun CtrlProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = CtrlColors.Text,
    trackColor: Color = CtrlColors.ProgressTrack,
) {
    val f = fraction.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(trackColor)) {
        Box(Modifier.fillMaxWidth(f).height(6.dp).background(color))
    }
}

/** Rounded-rect switch, 52x32dp: accent track and dark knob when on, grey when off. */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchTrackOff, label = "track")
    val knob by animateColorAsState(if (checked) CtrlColors.Ink else CtrlColors.Inactive, label = "knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "knobX")
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(
                    value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange,
                ) else Modifier
            )
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = x).size(24.dp).clip(RoundedCornerShape(7.dp)).background(knob))
    }
}

/** Main call to action: accent fill, dark text. */
@Composable
fun CtrlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = CtrlShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = CtrlColors.Accent,
            contentColor = CtrlColors.Ink,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Inactive,
        ),
        content = content,
    )
}

/** Secondary button: surface fill with a 1dp border. */
@Composable
fun CtrlSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = CtrlShapes.Button,
        border = BorderStroke(1.dp, CtrlColors.ControlBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CtrlColors.Surface,
            contentColor = CtrlColors.Text,
            disabledContainerColor = CtrlColors.Surface,
            disabledContentColor = CtrlColors.Inactive,
        ),
        content = content,
    )
}

@Composable
fun ctrlFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CtrlColors.Surface,
    unfocusedContainerColor = CtrlColors.Surface,
    disabledContainerColor = CtrlColors.Surface,
    errorContainerColor = CtrlColors.Surface,
    focusedBorderColor = CtrlColors.Accent,
    unfocusedBorderColor = CtrlColors.ControlBorder,
    disabledBorderColor = CtrlColors.CardBorder,
    cursorColor = CtrlColors.Accent,
    focusedLabelColor = CtrlColors.Accent,
    unfocusedLabelColor = CtrlColors.Muted,
    focusedTextColor = CtrlColors.Text,
    unfocusedTextColor = CtrlColors.Text,
    focusedPlaceholderColor = CtrlColors.Inactive,
    unfocusedPlaceholderColor = CtrlColors.Inactive,
    focusedTrailingIconColor = CtrlColors.Text,
    unfocusedTrailingIconColor = CtrlColors.Muted,
)

/** Text field: surface fill, 1dp border, 16dp corners, accent border and cursor when focused. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        label = label, placeholder = placeholder, trailingIcon = trailingIcon, suffix = suffix,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        shape = CtrlShapes.Field, colors = ctrlFieldColors(),
    )
}

@Composable
fun CtrlTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        label = label, placeholder = placeholder, trailingIcon = trailingIcon, suffix = suffix,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        shape = CtrlShapes.Field, colors = ctrlFieldColors(),
    )
}

/** Dialog: surface fill, 26dp corners, 1dp border. Same slots as Material's AlertDialog. */
@Composable
fun CtrlAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, CtrlColors.CardBorder, CtrlShapes.Dialog),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = CtrlShapes.Dialog,
        containerColor = CtrlColors.Surface,
        iconContentColor = CtrlColors.Accent,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
    )
}

/** Draws the list-row divider along the top of a row, inset by [inset] on both sides. */
fun Modifier.topDivider(inset: Dp = 0.dp, color: Color = CtrlColors.Divider): Modifier = drawBehind {
    val i = inset.toPx()
    drawRect(color, topLeft = Offset(i, 0f), size = Size(size.width - 2 * i, 1.dp.toPx()))
}

data class CtrlTab(val index: Int, val icon: ImageVector, val label: String, val badge: Int = 0)

/**
 * Floating bottom bar: 64dp tall, 22dp corners, 20dp from the sides and 28dp above the navigation bar.
 * The selected tab is an accent block with its icon and label; the others are icons only.
 */
@Composable
fun CtrlBottomBar(
    tabs: List<CtrlTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(CtrlShapes.Card)
                .background(CtrlColors.Surface.copy(alpha = 0.92f))
                .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Card)
                .selectableGroup()
                .padding(horizontal = 8.dp),
            horizontalArrangement = if (tabs.size > 3) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val isSel = t.index == selected
                val src = remember { MutableInteractionSource() }
                val bg by animateColorAsState(if (isSel) CtrlColors.Accent else Color.Transparent, label = "tabBg")
                val tint by animateColorAsState(if (isSel) CtrlColors.Ink else CtrlColors.Muted, label = "tabTint")
                Row(
                    Modifier
                        .height(48.dp)
                        .widthIn(min = 48.dp)
                        .pressScale(src, 0.92f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .selectable(
                            selected = isSel,
                            interactionSource = src,
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (!isSel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(t.index)
                            },
                        )
                        .animateContentSize()
                        .padding(horizontal = if (isSel) 18.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    BadgedBox(badge = { if (t.badge > 0) Badge { Text(if (t.badge > 99) "99+" else "${t.badge}") } }) {
                        Icon(t.icon, if (isSel) null else t.label, Modifier.size(if (isSel) 18.dp else 20.dp), tint = tint)
                    }
                    if (isSel) {
                        Spacer(Modifier.width(8.dp))
                        Text(t.label, fontFamily = CtrlFonts.Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = tint, maxLines = 1)
                    }
                }
            }
        }
    }
}
