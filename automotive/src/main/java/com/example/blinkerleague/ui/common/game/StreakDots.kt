package com.example.blinkerleague.ui.common.game

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.blinkerleague.ui.theme.Flame
import com.example.blinkerleague.ui.theme.Gold

/**
 * 연승 진행 점: 연속 보상 주기 [period]칸 중 [filled]칸을 불꽃색으로. 마지막(보상) 칸은 금색 테두리.
 * 칸이 많아도 폭 안에 들어가게 간격을 나눔. 칸 수는 `streakDotsFilled`(ui/achievements)로 구함
 */
@Composable
fun StreakDots(filled: Int, period: Int, modifier: Modifier = Modifier) {
    val empty = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val step = size.width / period
        val radius = minOf(size.height / 2, step * 0.35f)
        val stroke = 2.dp.toPx()
        repeat(period) { i ->
            val center = Offset(step * (i + 0.5f), size.height / 2)
            when {
                i < filled -> drawCircle(Flame, radius, center)
                i == period - 1 -> drawCircle(Gold, radius - stroke / 2, center, style = Stroke(stroke))
                else -> drawCircle(empty, radius - stroke / 2, center, style = Stroke(stroke))
            }
        }
    }
}
