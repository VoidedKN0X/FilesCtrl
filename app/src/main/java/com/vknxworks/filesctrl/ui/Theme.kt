package com.vknxworks.filesctrl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.vknxworks.filesctrl.ui.theme.CtrlColorScheme
import com.vknxworks.filesctrl.ui.theme.CtrlMaterialShapes
import com.vknxworks.filesctrl.ui.theme.CtrlTypography

/** FilesCtrl is dark only, like every Ctrl app in the Signal look: no light theme, no Material You colours. */
@Composable
fun isAppInDarkTheme(): Boolean = true

@Composable
fun FilesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CtrlColorScheme, typography = CtrlTypography, shapes = CtrlMaterialShapes, content = content)
}
