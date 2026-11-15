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
 */
class HarshDrivingDetector(private val config: HarshConfig) {

    fun detect(speeds: List<SpeedSample>, brakes: List<BrakeSample>): List<HarshEvent> = emptyList()
}
