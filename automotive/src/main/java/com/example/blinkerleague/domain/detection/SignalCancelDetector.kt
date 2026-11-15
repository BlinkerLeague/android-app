package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.SignalCancelConfig
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.Turn
import com.example.blinkerleague.domain.model.TurnSignalState

/** 깜빡이를 켠 채 잊은 구간 하나. [occurredAtMs]는 깜빡이를 켠 시각, [durationSec]는 켜져 있던 시간 */
data class SignalLeftOn(
    val occurredAtMs: Long,
    val durationSec: Int,
)

/**
 * 회전 없이 깜빡이를 오래 켜 둔 구간을 찾음 (서버 기준값 `signalCancel`).
 */
class SignalCancelDetector(private val config: SignalCancelConfig) {

    fun detect(
        signals: List<SignalSample>,
        turns: List<Turn>,
        speeds: List<SpeedSample>,
        tripStartMs: Long,
        tripEndMs: Long,
    ): List<SignalLeftOn> {
        val result = mutableListOf<SignalLeftOn>()
        var onSinceMs: Long? = null
        for (sample in signals) {
            val start = onSinceMs
            if (sample.state != TurnSignalState.OFF) {
                if (start == null) onSinceMs = sample.timestampMs
            } else if (start != null) {
                val offMs = sample.timestampMs
                // 운행 시작 전부터 켜져 있었으면 운행 시작부터, 구간 안에 회전이 있으면 마지막 회전이 끝난 뒤부터 셈
                val lastTurnEndMs = turns
                    .filter { it.startMs < offMs && it.endMs > start }
                    .maxOfOrNull { it.endMs }
                val countFromMs = maxOf(start, tripStartMs, lastTurnEndMs ?: start).coerceAtMost(offMs)
                val durationSec = (fastMillis(speeds, countFromMs, offMs) / 1_000).toInt()
                if (durationSec > config.forgetSec) result += SignalLeftOn(countFromMs, durationSec)
                onSinceMs = null
            }
        }
        return result
    }

    /**
     * [startMs, endMs] 중 minSpeedKmh 이상으로 달린 시간(ms).
     * 샘플 사이는 앞 샘플 값이 유지된다고 봄 (TripDistance.kmFromSpeeds와 같은 방식)
     */
    private fun fastMillis(speeds: List<SpeedSample>, startMs: Long, endMs: Long): Long {
        val sorted = speeds.sortedBy { it.timestampMs }
        var millis = 0L
        for ((i, s) in sorted.withIndex()) {
            if (s.speedKmh < config.minSpeedKmh) continue
            val from = maxOf(s.timestampMs, startMs)
            val to = minOf(sorted.getOrNull(i + 1)?.timestampMs ?: endMs, endMs)
            if (to > from) millis += to - from
        }
        return millis
    }
}
