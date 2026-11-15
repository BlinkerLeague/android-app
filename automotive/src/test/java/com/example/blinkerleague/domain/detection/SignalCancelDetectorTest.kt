package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.SignalCancelConfig
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.TurnSignalState.LEFT
import com.example.blinkerleague.domain.model.TurnSignalState.OFF
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalCancelDetectorTest {

    private val detector = SignalCancelDetector(SignalCancelConfig(forgetSec = 20, minSpeedKmh = 30))

    /** [fromMs]부터 [toMs]까지 1초마다 [kmh]로 달린 속도 샘플 */
    private fun constantSpeed(fromMs: Long, toMs: Long, kmh: Float) =
        (fromMs..toMs step 1_000).map { SpeedSample(it, kmh) }

    @Test
    fun `깜빡이가 회전 없이 forgetSec 넘게 minSpeedKmh 이상으로 켜져 있으면 한 번 잡는다`() {
        // given
        // 운행 0s ~ 120s 동안 50km/h로 직진, 10s에 왼쪽 깜빡이를 켜고 40s에 끔 (30초, 회전 없음)
        val signals = listOf(SignalSample(10_000, LEFT), SignalSample(40_000, OFF))
        val speeds = constantSpeed(0, 120_000, 50f)

        // when
        val result = detector.detect(
            signals = signals,
            turns = emptyList(),
            speeds = speeds,
            tripStartMs = 0,
            tripEndMs = 120_000,
        )

        // then
        assertEquals(listOf(SignalLeftOn(occurredAtMs = 10_000, durationSec = 30)), result)
    }
}
