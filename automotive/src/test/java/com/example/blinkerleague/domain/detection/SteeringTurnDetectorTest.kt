package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.TurnConfig
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnSignalState
import com.example.blinkerleague.domain.model.VehicleStateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringTurnDetectorTest {

    // 진입 90°, 해제 20°, 연속 회전 묶음 3초. 아래 테스트 값은 읽기 쉽게 "오른쪽 양수"로 씀
    private val config = TurnConfig(steeringRightPositive = true)
    private val detector = SteeringTurnDetector(config)

    /** (초, 각도) 목록 → 핸들 샘플 */
    private fun steering(vararg points: Pair<Int, Float>) = points.map { SteeringSample(it.first * 1_000L, it.second) }

    @Test
    fun `진입 기준을 넘으면 회전, 구간은 해제 기준을 넘은 때부터 다시 내려온 때까지`() {
        val turn = detector.detect(steering(0 to 0f, 10 to 30f, 11 to 120f, 14 to 40f, 15 to 10f)).single()
        assertEquals(TurnDirection.RIGHT, turn.direction)
        assertEquals(10_000L, turn.startMs)
        assertEquals(15_000L, turn.endMs)
        assertEquals(120f, turn.angleDeg, 0.01f)
        assertNull(turn.uTurn)
    }

    @Test
    fun `음수 각도는 좌회전`() {
        val turn = detector.detect(steering(0 to 0f, 5 to -130f, 9 to 0f)).single()
        assertEquals(TurnDirection.LEFT, turn.direction)
    }

    @Test
    fun `기본 설정은 SDK 실측 부호 - 핸들 음수가 우회전`() {
        val default = SteeringTurnDetector(TurnConfig())
        assertEquals(TurnDirection.RIGHT, default.detect(steering(0 to 0f, 5 to -130f, 9 to 0f)).single().direction)
        assertEquals(TurnDirection.LEFT, default.detect(steering(0 to 0f, 5 to 130f, 9 to 0f)).single().direction)
    }

    @Test
    fun `부호 설정을 바꾸면 좌우가 뒤집힘`() {
        val flipped = SteeringTurnDetector(config.copy(steeringRightPositive = false))
        assertEquals(TurnDirection.LEFT, flipped.detect(steering(0 to 0f, 5 to 130f, 9 to 0f)).single().direction)
    }

    @Test
    fun `진입 기준에 못 미치는 조향은 회전이 아님 (차로 변경, 완만한 커브)`() {
        assertTrue(detector.detect(steering(0 to 0f, 5 to 40f, 8 to 85f, 10 to 0f)).isEmpty())
    }

    @Test
    fun `90도 근처에서 흔들려도 해제 기준 아래로 내려오기 전까지는 한 번`() {
        val samples = steering(0 to 0f, 1 to 95f, 2 to 85f, 3 to 92f, 4 to 70f, 5 to 98f, 6 to 0f)
        assertEquals(1, detector.detect(samples).size)
    }

    @Test
    fun `기록이 회전 도중에 끝나면 마지막 샘플까지를 회전으로 봄`() {
        val turn = detector.detect(steering(0 to 0f, 5 to 120f, 8 to 120f)).single()
        assertEquals(8_000L, turn.endMs)
    }

    @Test
    fun `기어 P나 R에서 꺾은 것은 제외`() {
        val samples = steering(0 to 0f, 5 to 300f, 9 to 0f)
        val reverse = listOf(VehicleStateSample(0, true, Gear.D), VehicleStateSample(4_000, true, Gear.R))
        val park = listOf(VehicleStateSample(0, true, Gear.P))
        val drive = listOf(VehicleStateSample(0, true, Gear.P), VehicleStateSample(2_000, true, Gear.D))

        assertTrue(detector.detect(samples, vehicleStates = reverse).isEmpty())
        assertTrue(detector.detect(samples, vehicleStates = park).isEmpty())
        assertEquals(1, detector.detect(samples, vehicleStates = drive).size)
    }

    // 비상등은 현재 TurnSignal로 감지 불가(SDK에 값 없음). 다른 출처로 HAZARD 샘플이 들어올 때를 위한 규칙 테스트
    @Test
    fun `비상등이 켜져 있던 회전은 제외`() {
        val samples = steering(0 to 0f, 5 to 120f, 9 to 0f)
        val hazard = listOf(SignalSample(3_000, TurnSignalState.HAZARD), SignalSample(6_000, TurnSignalState.OFF))
        val hazardBefore = listOf(SignalSample(1_000, TurnSignalState.HAZARD), SignalSample(2_000, TurnSignalState.OFF))

        assertTrue(detector.detect(samples, signals = hazard).isEmpty())
        assertEquals(1, detector.detect(samples, signals = hazardBefore).size)
    }

    @Test
    fun `속도를 알 때 회전 내내 5km_h 미만이면 제외, 속도를 모르면 제외하지 않음`() {
        val samples = steering(0 to 0f, 5 to 120f, 9 to 0f)
        fun loc(sec: Int, speed: Float?) = LocationSample(sec * 1_000L, 0.0, 0.0, speed, null)

        val slow = (5..9).map { loc(it, 3f) }
        val moving = (5..9).map { loc(it, if (it == 7) 12f else 3f) }
        val unknown = (5..9).map { loc(it, null) }

        assertTrue(detector.detect(samples, locations = slow).isEmpty())
        assertEquals(1, detector.detect(samples, locations = moving).size)
        assertEquals(1, detector.detect(samples, locations = unknown).size)
    }

    @Test
    fun `차량 속도가 있으면 그것으로 저속 판단 - 회전 시작 전 마지막 값도 포함`() {
        val samples = steering(0 to 0f, 5 to 120f, 9 to 0f)
        val slow = listOf(SpeedSample(1_000, 3f), SpeedSample(7_000, 2f))
        val movingBefore = listOf(SpeedSample(1_000, 20f)) // 회전 중에는 샘플이 없지만 직전 값이 20km/h
        val reversing = listOf(SpeedSample(4_000, -12f)) // 후진은 음수지만 절댓값으로 봄

        assertTrue(detector.detect(samples, speeds = slow).isEmpty())
        assertEquals(1, detector.detect(samples, speeds = movingBefore).size)
        assertEquals(1, detector.detect(samples, speeds = reversing).size)
    }

    @Test
    fun `같은 방향 연속 회전은 하나로 합침`() {
        val samples = steering(0 to 0f, 5 to 120f, 8 to 0f, 10 to 150f, 13 to 0f)
        val turn = detector.detect(samples).single()
        assertEquals(5_000L, turn.startMs)
        assertEquals(13_000L, turn.endMs)
        assertEquals(150f, turn.angleDeg, 0.01f)
    }

    @Test
    fun `좌우가 섞인 연속 회전은 제외 - 0을 거치지 않고 바로 반대로 꺾어도 끊어서 봄`() {
        val samples = steering(0 to 0f, 5 to 120f, 8 to -120f, 12 to 120f, 14 to 0f)
        assertTrue(detector.detect(samples).isEmpty())
    }

    @Test
    fun `간격이 충분하면 따로 감지`() {
        val samples = steering(0 to 0f, 5 to -120f, 8 to 0f, 30 to 120f, 33 to 0f)
        assertEquals(listOf(TurnDirection.LEFT, TurnDirection.RIGHT), detector.detect(samples).map { it.direction })
    }

    @Test
    fun `샘플 순서가 섞여 들어와도 시각순으로 처리`() {
        val samples = steering(9 to 0f, 0 to 0f, 5 to 120f)
        assertEquals(1, detector.detect(samples).size)
    }
}
