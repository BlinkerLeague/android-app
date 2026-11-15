package com.example.blinkerleague.ui.common.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.example.blinkerleague.ui.theme.Flame
import com.example.blinkerleague.ui.theme.Gold
import com.example.blinkerleague.ui.theme.GradeColors
import kotlin.random.Random

private class Piece(val x: Float, val delay: Float, val drift: Float, val spin: Float, val color: Color)

/**
 * 꽃가루. 파티클 30개가 [startMs]부터 [durationMs](2초) 동안 한 번만 떨어지고 사라짐 (반복 · 점멸 없음, 시드 고정).
 * 터치를 받지 않음. Preview에서는 떨어지는 중간 모습을 그림.
 */
@Composable
fun Confetti(clock: RevealClock, startMs: Int, modifier: Modifier = Modifier, durationMs: Int = 2_000) {
    val pieces = remember {
        val random = Random(7)
        val colors = GradeColors + Flame + Gold
        List(30) { Piece(random.nextFloat(), random.nextFloat() * 0.3f, random.nextFloat() - 0.5f, random.nextFloat() * 540f, colors[it % colors.size]) }
    }
    val inspection = LocalInspectionMode.current
    Canvas(modifier.fillMaxSize()) {
        val p = if (inspection) 0.35f else (clock.elapsedMs - startMs) / durationMs
        if (p <= 0f || p >= 1f) return@Canvas
        val w = 10.dp.toPx()
        val h = 16.dp.toPx()
        val fall = size.height + 2 * h
        pieces.forEach { piece ->
            val t = ((p - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val center = Offset(piece.x * size.width + piece.drift * t * 120.dp.toPx(), -h + t * t * fall)
            rotate(piece.spin * t, center) {
                drawRect(piece.color.copy(alpha = 1f - t), Offset(center.x - w / 2, center.y - h / 2), Size(w, h))
            }
        }
    }
}
