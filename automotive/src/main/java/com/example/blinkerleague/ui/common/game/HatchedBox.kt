package com.example.blinkerleague.ui.common.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HatchBase = Color(0xFF2C333D)
private val HatchStripe = Color(0xFF262C35)

/** 사선 무늬 칸: 아직 측정하지 않는 항목(준비 중 · 미지원)의 빈 막대 */
@Composable
fun HatchedBox(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(HatchBase)
            .drawBehind {
                val step = 16.dp.toPx()
                val stroke = 8.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(HatchStripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stroke)
                    x += step
                }
            },
    )
}
