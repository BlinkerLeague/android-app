package com.example.blinkerleague.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityReportTest {

    @Test
    fun `방향지시등과 핸들 각도가 둘 다 있어야 회전 측정 가능`() {
        val all = CapabilityReport.ALL_SUPPORTED
        assertTrue(all.canMeasureTurns)
        assertEquals(setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE), all.supportedSignals)

        val noSteering = all.copy(steering = Availability.UNSUPPORTED)
        assertFalse(noSteering.canMeasureTurns)
        assertEquals(setOf(SignalType.TURN_SIGNAL), noSteering.supportedSignals)
    }

    @Test
    fun `속도 권한이 없어도 회전 측정과 supported 목록에는 영향 없음`() {
        val noSpeed = CapabilityReport.ALL_SUPPORTED.copy(speed = Availability.NO_PERMISSION)
        assertTrue(noSpeed.canMeasureTurns)
        assertEquals(setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE), noSpeed.supportedSignals)
    }
}
