package com.example.blinkerleague.ui.common.game

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 살짝 튕기는 도장 (dampingRatio 0.45, 약 550ms) */
private val StampSpring = FloatSpringSpec(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)

/**
 * 등급 도장. [startMs]에 작게 기울어진 상태에서 튕기며 −6°로 찍힘. 시작 전에는 보이지 않음.
 * [glow]는 맨 위 등급일 때 뒤쪽 은은한 빛 (반복 없음).
 */
@Composable
fun GradeStamp(
    grade: String,
    color: Color,
    clock: RevealClock,
    startMs: Int,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
) {
    BoxWithConstraints(
        modifier
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .graphicsLayer {
                val playMs = clock.elapsedMs - startMs
                val t = if (playMs <= 0f) 0f else StampSpring.getValueFromNanos((playMs * 1_000_000).toLong(), 0f, 1f, 0f)
                alpha = (t * 3f).coerceIn(0f, 1f)
                scaleX = 0.4f + 0.6f * t
                scaleY = scaleX
                rotationZ = -14f + 8f * t
            }
            .drawBehind {
                val radius = size.minDimension / 2
                if (glow) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)), radius * 1.25f)
                val ring = 6.dp.toPx()
                drawCircle(color, radius - ring / 2, style = Stroke(ring))
                val inner = 2.dp.toPx()
                drawCircle(
                    color.copy(alpha = 0.5f),
                    radius - ring - 8.dp.toPx(),
                    center = Offset(size.width / 2, size.height / 2),
                    style = Stroke(inner, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(grade, color = color, fontSize = (maxWidth.value * 0.5f).sp, fontWeight = FontWeight.Black)
    }
}
