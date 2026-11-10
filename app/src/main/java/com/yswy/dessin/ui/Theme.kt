package com.yswy.dessin.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * アプリ全体の配色。Web版の CSS の色に合わせる(ダイアログ・ボタン・入力欄など Material3 の部品に効く)。
 * Web版は明るい配色だけなので、夜間モードでも同じ配色にする。
 */
private val DessinColors = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    secondary = Palette.Ink,
    onSecondary = Color.White,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Panel,
    onSurface = Palette.Ink,
    onSurfaceVariant = Palette.Muted,
    // AlertDialog の背景は surfaceContainerHigh
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.Panel,
    surfaceContainerHighest = Palette.Panel,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Accent,
)

@Composable
fun DessinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DessinColors, content = content)
}
