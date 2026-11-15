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
 */
class HarshDrivingDetector(private val config: HarshConfig) {

    fun detect(speeds: List<SpeedSample>, brakes: List<BrakeSample>): List<HarshEvent> {
        val sorted = speeds.sortedBy { it.timestampMs }
        return sorted.mapNotNull { sample ->
            val before = sorted.lastOrNull { it.timestampMs <= sample.timestampMs - ONE_SECOND_MS } ?: return@mapNotNull null
            val delta = sample.speedKmh - before.speedKmh
            if (delta >= config.deltaKmhPerSec) HarshEvent(sample.timestampMs, HarshKind.ACCEL) else null
        }
    }

    private companion object {
        const val ONE_SECOND_MS = 1_000L
    }
}
