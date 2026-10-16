package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.data.ThemeMode

// Brand fallback when Material You is off: deep teal with a warm folder amber.
private val BrandDark = darkColorScheme(
    primary = Color(0xFF7DD3D8),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F54),
    onPrimaryContainer = Color(0xFF9CF0F4),
    secondary = Color(0xFFB1CBCD),
    onSecondary = Color(0xFF1C3436),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF643F00),
    onTertiaryContainer = Color(0xFFFFDDB6),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFDEE3E4),
    surface = Color(0xFF0F1415),
    onSurface = Color(0xFFDEE3E4),
    surfaceVariant = Color(0xFF3F4849),
    onSurfaceVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color(0xFF0A0F10),
    surfaceContainerLow = Color(0xFF171D1E),
    surfaceContainer = Color(0xFF1B2122),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
    outline = Color(0xFF899293),
    outlineVariant = Color(0xFF3F4849),
)

private val BrandLight = lightColorScheme(
    primary = Color(0xFF006A6F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF0F4),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF835400),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB6),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFF5FAFB),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFF5FAFB),
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4849),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F5),
    surfaceContainer = Color(0xFFE9EFEF),
    surfaceContainerHigh = Color(0xFFE3E9EA),
    surfaceContainerHighest = Color(0xFFDEE3E4),
    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFBEC8C9),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)

@Composable
fun isAppInDarkTheme(): Boolean = when (App.settings.theme) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun FilesTheme(content: @Composable () -> Unit) {
    val s = App.settings
    val dark = isAppInDarkTheme()
    val ctx = LocalContext.current
    var scheme = when {
        s.dynamicColor -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> BrandDark
        else -> BrandLight
    }
    if (dark && s.pureBlack) scheme = scheme.pureBlack()
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}
