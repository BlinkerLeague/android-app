package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.SignalCancelConfig
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.Turn

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
    ): List<SignalLeftOn> = emptyList()
}
