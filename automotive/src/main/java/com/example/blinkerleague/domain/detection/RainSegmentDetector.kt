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

/**
 * 와이퍼가 켜진 구간을 우천 구간으로 보고 그 동안 하향등이 꺼진 시간을 구함. 순수 함수.
 * 운행 구간 안에서 와이퍼가 연속으로 켜진 구간 중 [RainConfig.minWiperSec]보다 긴 것만 셈.
 * 운행 시작 때 켜져 있던 와이퍼는 운행 시작부터, 운행이 끝날 때까지 켜져 있으면 운행 끝에서 닫음.
 */
class RainSegmentDetector(private val config: RainConfig) {

    fun detect(
        wipers: List<WiperSample>,
        headlights: List<HeadlightSample>,
        tripStartMs: Long,
        tripEndMs: Long,
    ): List<RainSegment> {
        if (tripEndMs <= tripStartMs) return emptyList()
        val sortedHeadlights = headlights.sortedBy { it.timestampMs }
        return wiperOnIntervals(wipers.sortedBy { it.timestampMs }, tripStartMs, tripEndMs)
            .filter { (start, end) -> end - start > config.minWiperSec * 1000L }
            .map { (start, end) ->
                RainSegment(
                    occurredAtMs = start,
                    durationSec = ((end - start) / 1000).toInt(),
                    headlightOffSec = (HeadlightOff.millis(sortedHeadlights, start, end) / 1000).toInt(),
                    knownLimitSec = 0,
                    overRainLimitSec = 0,
                )
            }
    }

    /** 운행 구간 안에서 와이퍼가 켜져 있던 연속 구간들 */
    private fun wiperOnIntervals(sorted: List<WiperSample>, tripStartMs: Long, tripEndMs: Long): List<Pair<Long, Long>> {
        val intervals = mutableListOf<Pair<Long, Long>>()
        var onSince: Long? = if (sorted.lastOrNull { it.timestampMs <= tripStartMs }?.on == true) tripStartMs else null
        for (sample in sorted) {
            if (sample.timestampMs <= tripStartMs) continue
            if (sample.timestampMs >= tripEndMs) break
            val since = onSince
            if (sample.on && since == null) {
                onSince = sample.timestampMs
            } else if (!sample.on && since != null) {
                intervals += since to sample.timestampMs
                onSince = null
            }
        }
        onSince?.let { intervals += it to tripEndMs }
        return intervals
    }
}
