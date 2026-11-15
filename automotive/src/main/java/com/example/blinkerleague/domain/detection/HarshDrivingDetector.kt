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
 */
class HarshDrivingDetector(private val config: HarshConfig) {

    fun detect(speeds: List<SpeedSample>, brakes: List<BrakeSample>): List<HarshEvent> {
        val sorted = speeds.sortedBy { it.timestampMs }
        val sortedBrakes = brakes.sortedBy { it.timestampMs }
        val events = mutableListOf<HarshEvent>()
        var accelerating = false
        var braking = false
        for (sample in sorted) {
            val windowStartMs = sample.timestampMs - ONE_SECOND_MS
            val before = sorted.lastOrNull { it.timestampMs <= windowStartMs } ?: continue
            val delta = sample.speedKmh - before.speedKmh
            val accelOver = delta >= config.deltaKmhPerSec
            val brakeOver = delta <= -config.deltaKmhPerSec && brakePressedDuring(sortedBrakes, windowStartMs, sample.timestampMs)
            if (accelOver && !accelerating) events += HarshEvent(sample.timestampMs, HarshKind.ACCEL)
            if (brakeOver && !braking) events += HarshEvent(sample.timestampMs, HarshKind.BRAKE)
            accelerating = accelOver
            braking = brakeOver
        }
        return events
    }

    /**
     * [fromMs, toMs] 동안 브레이크가 눌린 적이 있는지. 브레이크 상태는 바뀔 때만 오므로
     * 구간 시작 때 유지 중인 상태([fromMs] 이전 마지막 샘플)도 봄.
     */
    private fun brakePressedDuring(brakes: List<BrakeSample>, fromMs: Long, toMs: Long): Boolean {
        val heldAtStart = brakes.lastOrNull { it.timestampMs <= fromMs }?.pressed == true
        return heldAtStart || brakes.any { it.pressed && it.timestampMs in fromMs..toMs }
    }

    private companion object {
        const val ONE_SECOND_MS = 1_000L
    }
}
