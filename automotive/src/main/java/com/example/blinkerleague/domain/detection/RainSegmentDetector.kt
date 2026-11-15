package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.RainConfig
import com.example.blinkerleague.domain.model.HeadlightSample
import com.example.blinkerleague.domain.model.WiperSample

/**
 * 우천 주행 구간 한 건 (이벤트 RAIN_SEGMENT). [occurredAtMs]는 구간 시작 시각.
 * 제한속도는 알 수 없어서 [knownLimitSec] · [overRainLimitSec]는 0
 */
data class RainSegment(
    val occurredAtMs: Long,
    val durationSec: Int,
    val headlightOffSec: Int,
    val knownLimitSec: Int,
    val overRainLimitSec: Int,
)

/** 와이퍼가 켜진 구간을 우천 구간으로 보고 그 동안 하향등이 꺼진 시간을 구함. 순수 함수 */
class RainSegmentDetector(private val config: RainConfig) {

    fun detect(
        wipers: List<WiperSample>,
        headlights: List<HeadlightSample>,
        tripStartMs: Long,
        tripEndMs: Long,
    ): List<RainSegment> = emptyList()
}
