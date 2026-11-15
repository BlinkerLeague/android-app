package com.example.blinkerleague.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityReportTest {

    @Test
    fun `방향지시등과 핸들 각도가 둘 다 있어야 회전 측정 가능`() {
        // 속도 · 브레이크 · 하향등은 이 테스트와 무관하므로 빼 둠
        val all = CapabilityReport.ALL_SUPPORTED.copy(
            speed = Availability.UNSUPPORTED,
            brake = Availability.UNSUPPORTED,
            headlight = Availability.UNSUPPORTED,
        )
        assertTrue(all.canMeasureTurns)
        assertEquals(setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE), all.supportedSignals)

        val noSteering = all.copy(steering = Availability.UNSUPPORTED)
        assertFalse(noSteering.canMeasureTurns)
        assertEquals(setOf(SignalType.TURN_SIGNAL), noSteering.supportedSignals)
    }

    @Test
    fun `속도 권한이 없어도 회전 측정과 supported 목록에는 영향 없음`() {
        val noSpeed = CapabilityReport.ALL_SUPPORTED.copy(
            speed = Availability.NO_PERMISSION,
            brake = Availability.UNSUPPORTED,
            headlight = Availability.UNSUPPORTED,
        )
        assertTrue(noSpeed.canMeasureTurns)
        assertEquals(setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE), noSpeed.supportedSignals)
    }

    @Test
    fun `속도가 SUPPORTED이면 supportedSignals에 SPEED를 넣는다`() {
        // given
        // 브레이크 · 하향등은 이 테스트와 무관하므로 빼 둠
        val report = CapabilityReport.ALL_SUPPORTED.copy(brake = Availability.UNSUPPORTED, headlight = Availability.UNSUPPORTED)

        // when
        val signals = report.supportedSignals

        // then
        assertEquals(setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE, SignalType.SPEED), signals)
    }

    @Test
    fun `브레이크가 SUPPORTED이면 supportedSignals에 BRAKE를 넣는다`() {
        // given
        // 브레이크만 지원하는 차량
        val report = CapabilityReport(
            turnSignal = Availability.UNSUPPORTED,
            steering = Availability.UNSUPPORTED,
            gear = Availability.UNSUPPORTED,
            speed = Availability.UNSUPPORTED,
            brake = Availability.SUPPORTED,
        )

        // when
        val signals = report.supportedSignals

        // then
        assertEquals(setOf(SignalType.BRAKE), signals)
    }

    @Test
    fun `하향등이 SUPPORTED이면 supportedSignals에 HEADLIGHT를 넣는다`() {
        // given
        // 하향등만 지원하는 차량
        val report = CapabilityReport(
            turnSignal = Availability.UNSUPPORTED,
            steering = Availability.UNSUPPORTED,
            gear = Availability.UNSUPPORTED,
            speed = Availability.UNSUPPORTED,
            headlight = Availability.SUPPORTED,
        )

        // when
        val signals = report.supportedSignals

        // then
        assertEquals(setOf(SignalType.HEADLIGHT), signals)
    }
}
