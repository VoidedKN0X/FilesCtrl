package com.vknxworks.filesctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.R

// "Ctrl / Signal": the shared look of the VKNXWorks Ctrl series. High contrast, one accent, and a dark and
// a light palette that follow the phone's dark theme setting.

/** One full set of Signal colours. Screens read the current one through [CtrlColors] / [LocalCtrlColors]. */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    /** Accent used for text and icons on this palette's backgrounds (the fill accent is too pale on white). */
    val accentText: Color,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val cardBorder: Color,
    val controlBorder: Color,
    val divider: Color,
    val text: Color,
    val muted: Color,
    val inactive: Color,
    val error: Color,
    val onError: Color,
    val switchTrackOff: Color,
    val switchKnobOff: Color,
    val progressTrack: Color,
    val pinEmpty: Color,
)

val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

object CtrlColors {
    /** The series accent (fills: hero cards, primary buttons, active tab, switch track, PIN cells). One place. */
    val Accent = Color(0xFFB48CFF)
    /** Content on the accent fill, in both palettes. */
    val Ink = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        accentText = Accent,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        cardBorder = Color(0xFF232328),
        controlBorder = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        inactive = Color(0xFF8C8C94),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        switchTrackOff = Color(0xFF2A2A30),
        switchKnobOff = Color(0xFF8C8C94),
        progressTrack = Color(0xFF26262C),
        pinEmpty = Color(0xFF3A3A41),
    )

    val Light = CtrlPalette(
        isDark = false,
        accentText = Color(0xFF6A3BDB),
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        cardBorder = Color(0xFFDEDED7),
        controlBorder = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        text = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        inactive = Color(0xFF74747C),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        switchTrackOff = Color(0xFFDCDCD5),
        switchKnobOff = Color(0xFF74747C),
        progressTrack = Color(0xFFE2E2DC),
        pinEmpty = Color(0xFFC9C9C1),
    )

    val current: CtrlPalette @Composable @ReadOnlyComposable get() = LocalCtrlColors.current

    val AccentText: Color @Composable @ReadOnlyComposable get() = current.accentText
    val Background: Color @Composable @ReadOnlyComposable get() = current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = current.raised
    val CardBorder: Color @Composable @ReadOnlyComposable get() = current.cardBorder
    val ControlBorder: Color @Composable @ReadOnlyComposable get() = current.controlBorder
    val Divider: Color @Composable @ReadOnlyComposable get() = current.divider
    val Text: Color @Composable @ReadOnlyComposable get() = current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = current.muted
    val Inactive: Color @Composable @ReadOnlyComposable get() = current.inactive
    val Error: Color @Composable @ReadOnlyComposable get() = current.error
    val OnError: Color @Composable @ReadOnlyComposable get() = current.onError
    val SwitchTrackOff: Color @Composable @ReadOnlyComposable get() = current.switchTrackOff
    val SwitchKnobOff: Color @Composable @ReadOnlyComposable get() = current.switchKnobOff
    val ProgressTrack: Color @Composable @ReadOnlyComposable get() = current.progressTrack
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = current.pinEmpty
}

object CtrlFonts {
    val Grotesk = FontFamily(
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
        Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
        Font(R.font.space_grotesk_bold, FontWeight.Bold),
    )
    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    )
}

/** Styles outside Material's type scale. */
object CtrlType {
    val Display = TextStyle(fontFamily = CtrlFonts.Grotesk, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 54.sp, letterSpacing = (-0.04).em)
    val Headline = TextStyle(fontFamily = CtrlFonts.Grotesk, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-0.035).em)
    /** A folder or page name at the top of a screen. */
    val PageTitle = TextStyle(fontFamily = CtrlFonts.Grotesk, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.03).em)
    val Title = TextStyle(fontFamily = CtrlFonts.Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    /** Section labels: mono, upper case, wide. */
    val Label = TextStyle(fontFamily = CtrlFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.16.em)
    val LabelStrong = Label.copy(fontWeight = FontWeight.SemiBold)
    /** Sizes, dates, addresses and other data. */
    val Meta = TextStyle(fontFamily = CtrlFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val Wordmark = TextStyle(fontFamily = CtrlFonts.Mono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.14.em)
    val BigNumber = TextStyle(fontFamily = CtrlFonts.Grotesk, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 58.sp, letterSpacing = (-0.05).em)
    val Key = TextStyle(fontFamily = CtrlFonts.Mono, fontWeight = FontWeight.Medium, fontSize = 26.sp)
}

object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Control = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(16.dp)
    val Field = RoundedCornerShape(16.dp)
    val Badge = RoundedCornerShape(10.dp)
    val Dot = RoundedCornerShape(2.dp)
    val Dialog = RoundedCornerShape(26.dp)
}

/** Material colours for [p], so stock Material pieces (menus, radio buttons, text buttons) match the palette. */
fun ctrlColorScheme(p: CtrlPalette): ColorScheme {
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        // Plain Material text buttons and radio buttons use primary as a text colour, so it's the text accent;
        // filled pieces use the containers, which carry the fill accent.
        primary = p.accentText,
        onPrimary = if (p.isDark) CtrlColors.Ink else Color.White,
        primaryContainer = CtrlColors.Accent,
        onPrimaryContainer = CtrlColors.Ink,
        inversePrimary = CtrlColors.Accent,
        secondary = p.text,
        onSecondary = p.background,
        secondaryContainer = CtrlColors.Accent,
        onSecondaryContainer = CtrlColors.Ink,
        tertiary = p.accentText,
        onTertiary = if (p.isDark) CtrlColors.Ink else Color.White,
        tertiaryContainer = CtrlColors.Accent,
        onTertiaryContainer = CtrlColors.Ink,
        background = p.background,
        onBackground = p.text,
        surface = p.background,
        onSurface = p.text,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.muted,
        surfaceTint = Color.Transparent,
        surfaceBright = p.raised,
        surfaceDim = p.background,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.raised,
        inverseSurface = p.text,
        inverseOnSurface = p.background,
        outline = p.controlBorder,
        outlineVariant = p.cardBorder,
        error = p.error,
        onError = p.onError,
        errorContainer = p.error,
        onErrorContainer = p.onError,
        scrim = Color.Black,
    )
}

val CtrlMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private fun g(weight: FontWeight, size: Int, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = CtrlFonts.Grotesk,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.em,
)

val CtrlTypography = Typography(
    displayLarge = g(FontWeight.Bold, 56, 54, -0.04),
    displayMedium = g(FontWeight.Bold, 48, 48, -0.04),
    displaySmall = g(FontWeight.Bold, 40, 40, -0.035),
    headlineLarge = g(FontWeight.Bold, 40, 40, -0.035),
    headlineMedium = g(FontWeight.Bold, 30, 32, -0.03),
    headlineSmall = g(FontWeight.Bold, 24, 28, -0.02),
    titleLarge = g(FontWeight.SemiBold, 20, 26),
    titleMedium = g(FontWeight.SemiBold, 18, 24),
    titleSmall = g(FontWeight.SemiBold, 14, 20),
    bodyLarge = g(FontWeight.Normal, 16, 22),
    bodyMedium = g(FontWeight.Normal, 14, 20),
    bodySmall = g(FontWeight.Normal, 12, 16),
    labelLarge = g(FontWeight.Medium, 14, 20),
    labelMedium = g(FontWeight.Medium, 12, 16),
    labelSmall = g(FontWeight.Medium, 11, 16),
)
