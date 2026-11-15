package com.example.blinkerleague.ui.common.game

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt

/** 숫자 카운트업 (0 → [target], 600ms ease-out). [format]으로 "+3P 적립"처럼 문구에 넣음 */
@Composable
fun CountUpText(
    target: Int,
    clock: RevealClock,
    startMs: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    durationMs: Int = 600,
    format: @Composable (Int) -> String = { it.toString() },
) {
    val value = (target * clock.progress(startMs, durationMs, LinearOutSlowInEasing)).roundToInt()
    Text(format(value), modifier = modifier, style = style, color = color)
}
