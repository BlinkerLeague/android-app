package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.HarshConfig
import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.SpeedSample

/** 급가속 · 급감속 종류 */
enum class HarshKind { ACCEL, BRAKE }

/** 급가속 · 급감속 한 건. [occurredAtMs]는 기준을 넘은 속도 샘플의 시각 */
data class HarshEvent(
    val occurredAtMs: Long,
    val kind: HarshKind,
)

/**
 * 1초 사이 속도 변화로 급가속 · 급감속을 찾음 (서버 기준값 `harsh`).
 * 각 속도 샘플을 1초 전 시점의 속도(그 시각 이전의 마지막 샘플)와 비교함. 순수 함수.
 * - 급가속: 속도가 deltaKmhPerSec 이상 늘어남
 * - 급감속: 속도가 deltaKmhPerSec 이상 줄고, 그 1초 동안 브레이크가 눌린 적이 있음
 * 기준 이상이 이어지는 동안은 첫 샘플만 한 건으로 셈 (급가속 · 급감속 따로).
 * 1초 전 시점의 샘플이 그보다 maxGapSec 넘게 묵었으면(샘플 끊김) 판정하지 않음.
 */
class HarshDrivingDetector(private val config: HarshConfig) {

    fun detect(speeds: List<SpeedSample>, brakes: List<BrakeSample>): List<HarshEvent> {
        val sorted = speeds.sortedBy { it.timestampMs }
        val brakeWindow = BrakeWindow(brakes.sortedBy { it.timestampMs })
        val events = mutableListOf<HarshEvent>()
        var accelerating = false
        var braking = false
        // 1초 전 시점의 샘플(windowStartMs 이전 마지막 샘플) 위치. 샘플 시각 순으로 돌므로 앞으로만 움직임
        var beforeIndex = -1
        for (sample in sorted) {
            val windowStartMs = sample.timestampMs - ONE_SECOND_MS
            while (beforeIndex + 1 < sorted.size && sorted[beforeIndex + 1].timestampMs <= windowStartMs) beforeIndex++
            brakeWindow.moveTo(windowStartMs, sample.timestampMs)
            val before = sorted.getOrNull(beforeIndex) ?: continue
            if (windowStartMs - before.timestampMs > config.maxGapSec * 1_000L) continue
            val delta = sample.speedKmh - before.speedKmh
            val accelOver = delta >= config.deltaKmhPerSec
            val brakeOver = delta <= -config.deltaKmhPerSec && brakeWindow.pressedDuring()
            if (accelOver && !accelerating) events += HarshEvent(sample.timestampMs, HarshKind.ACCEL)
            if (brakeOver && !braking) events += HarshEvent(sample.timestampMs, HarshKind.BRAKE)
            accelerating = accelOver
            braking = brakeOver
        }
        return events
    }

    /**
     * [fromMs, toMs] 동안 브레이크가 눌린 적이 있는지 봄. 브레이크 상태는 바뀔 때만 오므로
     * 구간 시작 때 유지 중인 상태([fromMs] 이전 마지막 샘플)도 봄.
     * 구간은 앞으로만 움직이므로 포인터와 눌림 누적 개수로 샘플 수에 비례하는 시간에 끝냄.
     */
    private class BrakeWindow(private val brakes: List<BrakeSample>) {
        /** pressedBefore[k] = 앞에서 k개 샘플 중 눌림 샘플 수 */
        private val pressedBefore = IntArray(brakes.size + 1).also { counts ->
            brakes.forEachIndexed { i, b -> counts[i + 1] = counts[i] + if (b.pressed) 1 else 0 }
        }
        private var fromIndex = 0 // 시각이 fromMs 이상인 첫 샘플
        private var toIndex = 0 // 시각이 toMs 넘는 첫 샘플
        private var heldIndex = -1 // 시각이 fromMs 이하인 마지막 샘플

        fun moveTo(fromMs: Long, toMs: Long) {
            while (fromIndex < brakes.size && brakes[fromIndex].timestampMs < fromMs) fromIndex++
            while (heldIndex + 1 < brakes.size && brakes[heldIndex + 1].timestampMs <= fromMs) heldIndex++
            if (toIndex < fromIndex) toIndex = fromIndex
            while (toIndex < brakes.size && brakes[toIndex].timestampMs <= toMs) toIndex++
        }

        fun pressedDuring(): Boolean {
            val heldAtStart = brakes.getOrNull(heldIndex)?.pressed == true
            return heldAtStart || pressedBefore[toIndex] - pressedBefore[fromIndex] > 0
        }
    }

    private companion object {
        const val ONE_SECOND_MS = 1_000L
    }
}
