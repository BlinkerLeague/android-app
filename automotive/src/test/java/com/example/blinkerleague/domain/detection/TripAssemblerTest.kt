package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.data.scenario.Scenarios
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.TripEvent
import com.example.blinkerleague.domain.model.TurnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripAssemblerTest {

    private var seq = 0
    private val assembler = TripAssembler(DetectionConfig.DEFAULT) { "event-${++seq}" }
    private val demo = Scenarios.demo
    private val all = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE)

    private fun assemble(supported: Set<SignalType> = all, endedAtMs: Long = demo.durationMs) = assembler.assemble(
        tripId = "trip-1",
        startedAtMs = 0,
        endedAtMs = endedAtMs,
        recording = TripRecording(demo.steering, demo.signals, demo.vehicleStates, demo.locations),
        supported = supported,
    )

    @Test
    fun `시연 시나리오는 좌회전 성공, 우회전 실패 이벤트 2개`() {
        val trip = assemble()

        assertEquals("trip-1", trip.id)
        assertEquals(listOf("event-1", "event-2"), trip.events.map { it.id })
        val turns = trip.events.filterIsInstance<TripEvent.TurnEvent>()
        assertEquals(listOf(TurnDirection.LEFT to true, TurnDirection.RIGHT to false), turns.map { it.direction to it.signalOn })
        assertTrue(trip.distanceKm > 1.0)
    }

    @Test
    fun `방향지시등이나 핸들 각도 중 하나라도 지원하지 않으면 회전 이벤트를 만들지 않음`() {
        assertTrue(assemble(emptySet()).events.isEmpty())
        assertTrue(assemble(setOf(SignalType.TURN_SIGNAL)).events.isEmpty())
        assertTrue(assemble(setOf(SignalType.STEERING_ANGLE)).events.isEmpty())
        assertEquals(setOf(SignalType.TURN_SIGNAL), assemble(setOf(SignalType.TURN_SIGNAL)).supported)
    }

    @Test
    fun `운행 구간 밖의 위치는 쓰지 않음`() {
        // 첫 회전 직전에 운행이 끝났다고 보면 회전도 없고 거리도 줄어듦
        val trip = assemble(endedAtMs = 30_000)
        assertTrue(trip.events.isEmpty())
        assertTrue(trip.distanceKm < 0.5)
    }
}
