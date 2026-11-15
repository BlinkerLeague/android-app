package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.SignalCancelConfig
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.Turn
import com.example.blinkerleague.domain.model.TurnDirection
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

    @Test
    fun `회전 전에 오래 켜 둔 깜빡이는 잡지 않는다`() {
        // given
        // 50km/h로 달리다 10s에 왼쪽 깜빡이를 켜고, 40s ~ 45s 왼쪽 회전 뒤 46s에 끔 (회전 전 30초 켜 둠)
        val signals = listOf(SignalSample(10_000, LEFT), SignalSample(46_000, OFF))
        val turns = listOf(Turn(40_000, 45_000, TurnDirection.LEFT, 90f, uTurn = false))
        val speeds = constantSpeed(0, 120_000, 50f)

        // when
        val result = detector.detect(
            signals = signals,
            turns = turns,
            speeds = speeds,
            tripStartMs = 0,
            tripEndMs = 120_000,
        )

        // then
        assertEquals(emptyList<SignalLeftOn>(), result)
    }

    @Test
    fun `minSpeedKmh 미만으로 달린 시간은 세지 않는다`() {
        // given
        // 20km/h로 천천히 달리며 10s에 왼쪽 깜빡이를 켜고 50s에 끔 (40초, 회전 없음)
        val signals = listOf(SignalSample(10_000, LEFT), SignalSample(50_000, OFF))
        val speeds = constantSpeed(0, 120_000, 20f)

        // when
        val result = detector.detect(
            signals = signals,
            turns = emptyList(),
            speeds = speeds,
            tripStartMs = 0,
            tripEndMs = 120_000,
        )

        // then
        assertEquals(emptyList<SignalLeftOn>(), result)
    }

    @Test
    fun `운행 시작 전부터 켜져 있던 깜빡이는 운행 시작부터 재고 이벤트 시각도 운행 안에 둔다`() {
        // given
        // 운행 30s ~ 150s. 운행 전 10s에 왼쪽 깜빡이를 켜고 80s에 끔, 회전 없이 50km/h
        val signals = listOf(SignalSample(10_000, LEFT), SignalSample(80_000, OFF))
        val speeds = constantSpeed(0, 150_000, 50f)

        // when
        val result = detector.detect(
            signals = signals,
            turns = emptyList(),
            speeds = speeds,
            tripStartMs = 30_000,
            tripEndMs = 150_000,
        )

        // then
        assertEquals(listOf(SignalLeftOn(occurredAtMs = 30_000, durationSec = 50)), result)
    }

    @Test
    fun `운행이 끝날 때까지 끄지 않은 깜빡이도 운행 끝에서 구간을 닫아 잡는다`() {
        // given
        // 운행 0s ~ 60s. 10s에 왼쪽 깜빡이를 켜고 운행이 끝날 때까지 끄지 않음, 회전 없이 50km/h
        val signals = listOf(SignalSample(10_000, LEFT))
        val speeds = constantSpeed(0, 60_000, 50f)

        // when
        val result = detector.detect(
            signals = signals,
            turns = emptyList(),
            speeds = speeds,
            tripStartMs = 0,
            tripEndMs = 60_000,
        )

        // then
        assertEquals(listOf(SignalLeftOn(occurredAtMs = 10_000, durationSec = 50)), result)
    }
}
