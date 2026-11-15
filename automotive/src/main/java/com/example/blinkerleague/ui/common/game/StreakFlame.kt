package com.example.blinkerleague.ui.common.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import com.example.blinkerleague.ui.theme.Flame
import com.example.blinkerleague.ui.theme.Gold

/** 연승 불꽃. 정차 화면에서만 1.6초 주기로 크기가 6% 안에서 천천히 변함 (점멸 없음). 그 밖에는 멈춘 상태 */
@Composable
fun StreakFlame(modifier: Modifier = Modifier) {
    val scale = if (motionEnabled()) {
        rememberInfiniteTransition(label = "flame").animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "flameScale",
        ).value
    } else {
        1f
    }
    Canvas(modifier.aspectRatio(0.8f)) {
        scale(scale, pivot = center.copy(y = size.height)) {
            drawPath(flame(size.width, size.height), Flame)
            drawPath(flame(size.width * 0.5f, size.height * 0.55f, offsetX = size.width * 0.25f, offsetY = size.height * 0.45f), Gold)
        }
    }
}

private fun flame(w: Float, h: Float, offsetX: Float = 0f, offsetY: Float = 0f) = Path().apply {
    moveTo(offsetX + w / 2, offsetY)
    cubicTo(offsetX + w * 0.95f, offsetY + h * 0.4f, offsetX + w, offsetY + h * 0.75f, offsetX + w / 2, offsetY + h)
    cubicTo(offsetX, offsetY + h * 0.75f, offsetX + w * 0.05f, offsetY + h * 0.4f, offsetX + w / 2, offsetY)
    close()
}
