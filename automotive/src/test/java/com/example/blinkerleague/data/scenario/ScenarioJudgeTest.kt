package com.example.blinkerleague.data.scenario

import com.example.blinkerleague.data.location.FakeLocationSource
import com.example.blinkerleague.data.vehicle.FakeVehicleSignalSource
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.detection.EndReason
import com.example.blinkerleague.domain.detection.SignalJudge
import com.example.blinkerleague.domain.detection.TripBoundary
import com.example.blinkerleague.domain.detection.TripDistance
import com.example.blinkerleague.domain.detection.TripSegmenter
import com.example.blinkerleague.domain.detection.SteeringTurnDetector
import com.example.blinkerleague.domain.model.JudgedTurn
import com.example.blinkerleague.domain.model.TurnDirection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan.md 2단계 시나리오를 재생해 핸들 각도 회전 감지 + 깜빡이 판정 결과를 확인함 */
class ScenarioJudgeTest {

    private val config = DetectionConfig.DEFAULT
    private val detector = SteeringTurnDetector(config.turn)
    private val judge = SignalJudge(config.turn)

    private fun judge(s: Scenario): List<JudgedTurn> =
        judge.judge(detector.detect(s.steering, s.vehicleStates, s.signals, s.locations), s.signals)

    @Test
    fun `좌회전 + 깜빡이 - 성공 1회`() {
        val result = judge(Scenarios.leftTurnWithSignal).single()
        assertEquals(TurnDirection.LEFT, result.turn.direction)
        assertTrue(result.signalOn)
    }

    @Test
    fun `우회전 + 깜빡이 없음 - 실패 1회`() {
        val result = judge(Scenarios.rightTurnWithoutSignal).single()
        assertEquals(TurnDirection.RIGHT, result.turn.direction)
        assertEquals(false, result.signalOn)
    }

    @Test
    fun `미션 달성 시나리오 - 짧은 운행 기준 이상, 회전 3번 모두 성공`() {
        val s = Scenarios.missionAchieved
        assertEquals(listOf(true, true, true), judge(s).map { it.signalOn })
        assertTrue(TripDistance.km(s.locations) > config.trip.minDistanceKm)
    }

    @Test
    fun `회전교차로 - 판정 대상 회전 없음`() {
        assertTrue(judge(Scenarios.roundabout).isEmpty())
    }

    @Test
    fun `주차장 이동 - 판정 대상 회전 없음`() {
        assertTrue(judge(Scenarios.parkingLot).isEmpty())
    }

    @Test
    fun `시연 시나리오 - 좌회전 성공, 우회전 실패, 시동 off로 운행 종료`() {
        val s = Scenarios.demo
        assertEquals(
            listOf(TurnDirection.LEFT to true, TurnDirection.RIGHT to false),
            judge(s).map { it.turn.direction to it.signalOn },
        )

        val segmenter = TripSegmenter(config.trip)
        val boundaries = s.vehicleStates.mapNotNull(segmenter::onVehicleState)
        assertEquals(TripBoundary.Started(0), boundaries.first())
        assertEquals(EndReason.IGNITION_OFF, (boundaries.last() as TripBoundary.Ended).reason)
        assertTrue(TripDistance.km(s.locations) > config.trip.minDistanceKm)
    }

    @Test
    fun `Fake 공급원은 시나리오 시각대로 재생하고 타임스탬프에 시작 시각을 더함`() = runTest {
        val s = Scenarios.leftTurnWithSignal
        val startMs = 1_700_000_000_000L
        val locations = FakeLocationSource(s, startMs = startMs, speed = 2f).locations.toList()

        assertEquals(s.locations.size, locations.size)
        assertEquals(startMs + s.locations.last().timestampMs, locations.last().timestampMs)
        assertEquals(s.locations.last().timestampMs / 2, currentTime) // 2배속

        val signals = FakeVehicleSignalSource(s, startMs = startMs).turnSignals.toList()
        assertEquals(s.signals.map { startMs + it.timestampMs }, signals.map { it.timestampMs })
        val steering = FakeVehicleSignalSource(s, startMs = startMs).steeringAngles.toList()
        assertEquals(s.steering.map { it.angleDeg }, steering.map { it.angleDeg })
    }
}
