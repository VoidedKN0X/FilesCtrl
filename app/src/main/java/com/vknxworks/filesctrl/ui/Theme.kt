package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlMaterialShapes
import com.vknxworks.filesctrl.ui.theme.CtrlTypography
import com.vknxworks.filesctrl.ui.theme.LocalCtrlColors
import com.vknxworks.filesctrl.ui.theme.ctrlColorScheme

/**
 * FilesCtrl follows the phone's dark theme setting: Signal dark when it's on, Signal light when it's off.
 * There's no in-app theme choice and no Material You colours.
 */
@Composable
fun isAppInDarkTheme(): Boolean = isSystemInDarkTheme()

@Composable
fun FilesTheme(content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme()
    val palette = if (dark) CtrlColors.Dark else CtrlColors.Light
    val scheme = remember(palette) { ctrlColorScheme(palette) }
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(colorScheme = scheme, typography = CtrlTypography, shapes = CtrlMaterialShapes, content = content)
    }
}
