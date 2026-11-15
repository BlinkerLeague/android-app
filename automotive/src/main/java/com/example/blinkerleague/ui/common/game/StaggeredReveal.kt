package com.example.blinkerleague.ui.common.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 화면 진입 연출의 시계 하나 (0 → [totalMs]). 카드 순차 등장 · 카운트업 · 도장 · 꽃가루가 모두 이 시계를 보고 그려서
 * 탭 한 번으로 전부 최종 상태로 건너뛸 수 있음.
 */
@Stable
class RevealClock internal constructor(
    private val time: Animatable<Float, AnimationVector1D>,
    val totalMs: Int,
    private val onSkip: () -> Unit,
) {
    /** 진입 뒤 흐른 시간(ms). 건너뛰었거나 이미 본 화면이면 [totalMs] */
    val elapsedMs: Float get() = time.value

    /** [startMs]부터 [durationMs] 동안 0 → 1 */
    fun progress(startMs: Int, durationMs: Int, easing: Easing = FastOutSlowInEasing): Float =
        easing.transform(((elapsedMs - startMs) / durationMs).coerceIn(0f, 1f))

    /** 남은 연출을 건너뛰고 최종 상태로 */
    fun skip() = onSkip()
}

/**
 * 진입 연출 시계. 한 번 끝나거나 건너뛰면 rememberSaveable로 기억해, 다른 화면(플레이 보기)에서 돌아와도 다시 재생하지 않음.
 * 정차 화면이 아니면(LocalParked == false) 시작하지 않고 멈춰 있다가 P가 되면 이어서 재생함.
 */
@Composable
fun rememberRevealClock(totalMs: Int): RevealClock {
    val inspection = LocalInspectionMode.current
    val parked = LocalParked.current
    var played by rememberSaveable { mutableStateOf(false) }
    val time = remember { Animatable(if (played || inspection) totalMs.toFloat() else 0f) }
    LaunchedEffect(played, parked, inspection) {
        when {
            played || inspection -> time.snapTo(totalMs.toFloat())
            // 주행 중에는 기록 중 화면이 덮고 있으므로 연출을 시작하지 않음 (진행 중이던 연출은 이 효과가 취소되며 멈춤)
            !parked -> Unit
            else -> {
                val remainingMs = (totalMs - time.value).toInt().coerceAtLeast(0)
                time.animateTo(totalMs.toFloat(), tween(remainingMs, easing = LinearEasing))
                played = true
            }
        }
    }
    return remember(time) { RevealClock(time, totalMs) { played = true } }
}

/** 순차 등장: [startMs]부터 [durationMs] 동안 투명 → 불투명, 아래(또는 옆)에서 제자리로 */
fun Modifier.reveal(
    clock: RevealClock,
    startMs: Int,
    durationMs: Int = 400,
    fromX: Dp = 0.dp,
    fromY: Dp = 18.dp,
): Modifier = graphicsLayer {
    val p = clock.progress(startMs, durationMs)
    alpha = p
    translationX = (1 - p) * fromX.toPx()
    translationY = (1 - p) * fromY.toPx()
}

/**
 * 화면 어디를 눌러도 남은 연출을 건너뜀.
 * 연출 중의 탭은 건너뛰기만 하고 소비함: 아직 투명한 배너 · 카드 · 버튼이 눌려 다른 화면으로 넘어가지 않게 (리뷰 8).
 * 연출이 끝난 뒤의 탭은 소비하지 않아 누른 것(버튼 등)이 그대로 동작함
 */
fun Modifier.skipRevealOnTap(clock: RevealClock): Modifier = pointerInput(clock) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (clock.elapsedMs >= clock.totalMs) return@awaitEachGesture
        clock.skip()
        down.consume()
        // 손을 뗄 때까지 같은 제스처의 이벤트를 모두 소비 (자식의 클릭 · 스크롤이 시작되지 않게)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
