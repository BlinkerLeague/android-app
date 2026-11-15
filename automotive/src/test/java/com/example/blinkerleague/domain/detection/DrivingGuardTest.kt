package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.GuardConfig
import com.example.blinkerleague.domain.model.Gear
import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/screen-plan.md 3장 표의 모든 행 */
class DrivingGuardTest {
    private val config = GuardConfig(parkedMaxSpeedKmh = 1f)

    private fun eval(gear: Gear?, speed: Float?) = DrivingGuard.evaluate(gear, speed, config)

    @Test
    fun `P에서 멈춰 있거나 속도를 모르면 정차`() {
        assertEquals(GuardState.PARKED, eval(Gear.P, 0f))
        assertEquals(GuardState.PARKED, eval(Gear.P, 0.8f)) // 센서 잡음
        assertEquals(GuardState.PARKED, eval(Gear.P, null)) // 속도 권한 없음
    }

    @Test
    fun `P인데 움직이면 안전 쪽으로 주행`() {
        assertEquals(GuardState.DRIVING, eval(Gear.P, 5f))
        assertEquals(GuardState.DRIVING, eval(Gear.P, -3f)) // 후진 속도는 음수
    }

    @Test
    fun `D·R·N은 속도와 상관없이 주행 (신호 대기도 주행)`() {
        for (gear in listOf(Gear.D, Gear.R, Gear.N)) {
            assertEquals(GuardState.DRIVING, eval(gear, 0f))
            assertEquals(GuardState.DRIVING, eval(gear, null))
            assertEquals(GuardState.DRIVING, eval(gear, 60f))
        }
    }

    @Test
    fun `기어를 모르면 화면을 열지 않음`() {
        assertEquals(GuardState.VEHICLE_UNKNOWN, eval(Gear.UNKNOWN, 0f))
        assertEquals(GuardState.VEHICLE_UNKNOWN, eval(null, null))
    }
}
