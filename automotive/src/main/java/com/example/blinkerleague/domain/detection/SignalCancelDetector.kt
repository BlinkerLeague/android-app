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
                // 구간 안에 회전이 있으면 마지막 회전이 끝난 뒤부터 셈
                val countFromMs = turns
                    .filter { it.startMs < offMs && it.endMs > start }
                    .maxOfOrNull { it.endMs }
                    ?.coerceIn(start, offMs)
                    ?: start
                val durationSec = ((offMs - countFromMs) / 1_000).toInt()
                if (durationSec > config.forgetSec) result += SignalLeftOn(countFromMs, durationSec)
                onSinceMs = null
            }
        }
        return result
    }
}
