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
        var onState = TurnSignalState.OFF
        var onSinceMs: Long? = null
        for (sample in signals) {
            if (sample.state == onState) continue
            // 끄거나 방향을 바꾸면 이전 구간을 이 시각에 닫음
            onSinceMs?.let { start ->
                leftOnOrNull(start, sample.timestampMs, turns, speeds, tripStartMs, tripEndMs)?.let { result += it }
            }
            onState = sample.state
            onSinceMs = if (sample.state != TurnSignalState.OFF) sample.timestampMs else null
        }
        // 운행이 끝날 때까지 끄지 않았으면 운행 끝에서 닫음
        onSinceMs?.let { start ->
            leftOnOrNull(start, tripEndMs, turns, speeds, tripStartMs, tripEndMs)?.let { result += it }
        }
        return result
    }

    /** 켠 시각 [onMs]부터 끈 시각 [offMs]까지의 구간 하나를 판정. 운행 밖 시간은 세지 않음 */
    private fun leftOnOrNull(
        onMs: Long,
        offMs: Long,
        turns: List<Turn>,
        speeds: List<SpeedSample>,
        tripStartMs: Long,
        tripEndMs: Long,
    ): SignalLeftOn? {
        val countUntilMs = minOf(offMs, tripEndMs)
        // 운행 시작 전부터 켜져 있었으면 운행 시작부터, 구간 안에 회전이 있으면 마지막 회전이 끝난 뒤부터 셈
        val lastTurnEndMs = turns
            .filter { it.startMs < countUntilMs && it.endMs > onMs }
            .maxOfOrNull { it.endMs }
        val countFromMs = maxOf(onMs, tripStartMs, lastTurnEndMs ?: onMs).coerceAtMost(countUntilMs)
        val durationSec = (fastMillis(speeds, countFromMs, countUntilMs) / 1_000).toInt()
        return if (durationSec > config.forgetSec) SignalLeftOn(countFromMs, durationSec) else null
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
