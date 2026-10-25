package com.vknxworks.filesctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.R

// "Ctrl / Signal": the shared look of the VKNXWorks Ctrl series. Dark only, high contrast, one accent.

object CtrlColors {
    /** The series accent. Change it here and the whole app follows. */
    val Accent = Color(0xFFB48CFF)

    val Background = Color(0xFF08080A)
    val Surface = Color(0xFF141417)
    val Raised = Color(0xFF1C1C21)
    val CardBorder = Color(0xFF232328)
    val ControlBorder = Color(0xFF2A2A2F)
    val Divider = Color(0xFF1E1E23)
    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    val Inactive = Color(0xFF8C8C94)
    val Ink = Color(0xFF08080A)
    val Error = Color(0xFFFF6B6B)

    val SwitchTrackOff = Color(0xFF2A2A30)
    val ProgressTrack = Color(0xFF26262C)
    val PinEmpty = Color(0xFF3A3A41)
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

val CtrlColorScheme: ColorScheme = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.Ink,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.Ink,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.Text,
    onSecondary = CtrlColors.Ink,
    // Selected chips, segmented buttons and the like follow the accent too.
    secondaryContainer = CtrlColors.Accent,
    onSecondaryContainer = CtrlColors.Ink,
    tertiary = CtrlColors.Accent,
    onTertiary = CtrlColors.Ink,
    tertiaryContainer = CtrlColors.Accent,
    onTertiaryContainer = CtrlColors.Ink,
    background = CtrlColors.Background,
    onBackground = CtrlColors.Text,
    surface = CtrlColors.Background,
    onSurface = CtrlColors.Text,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = Color.Transparent,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = CtrlColors.Surface,
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Surface,
    surfaceContainerHighest = CtrlColors.Raised,
    inverseSurface = CtrlColors.Text,
    inverseOnSurface = CtrlColors.Ink,
    outline = CtrlColors.ControlBorder,
    outlineVariant = CtrlColors.CardBorder,
    error = CtrlColors.Error,
    onError = CtrlColors.Ink,
    errorContainer = CtrlColors.Error,
    onErrorContainer = CtrlColors.Ink,
    scrim = Color.Black,
)

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
