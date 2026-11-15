package com.example.blinkerleague.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BlinkerColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = OutlineStrong,
    outlineVariant = Outline,
)

/** 차량 화면은 항상 다크 테마로 그림 */
@Composable
fun BlinkerLeagueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlinkerColorScheme,
        typography = BlinkerTypography,
        content = content,
    )
}
