package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.data.scenario.Scenarios
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.TripEvent
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnSignalState
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
    fun `속도가 있으면 거리는 속도 적분, 없으면 위치로 계산`() {
        val withSpeeds = assembler.assemble(
            tripId = "t", startedAtMs = 0, endedAtMs = 60_000,
            recording = TripRecording(emptyList(), emptyList(), emptyList(), demo.locations, listOf(SpeedSample(0, 60f))),
            supported = all,
        )
        assertEquals(1.0, withSpeeds.distanceKm, 1e-9)

        val withoutSpeeds = assembler.assemble(
            tripId = "t", startedAtMs = 0, endedAtMs = 60_000,
            recording = TripRecording(emptyList(), emptyList(), emptyList(), demo.locations),
            supported = all,
        )
        assertEquals(TripDistance.km(demo.locations.filter { it.timestampMs <= 60_000 }), withoutSpeeds.distanceKm, 1e-9)
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
        assertTrue(trip.distanceKm < assemble().distanceKm / 4)
    }

    @Test
    fun `방향지시등 · 핸들 각도 · 속도가 지원되면 조립 결과에 SIGNAL_LEFT_ON 이벤트를 만든다`() {
        // given
        // 운행 0s ~ 120s, 회전 없이 50km/h. 10s에 왼쪽 깜빡이를 켜고 50s에 끔 (40초, 기본 기준 20초 초과)
        val signals = listOf(SignalSample(10_000, TurnSignalState.LEFT), SignalSample(50_000, TurnSignalState.OFF))
        val speeds = (0L..120_000L step 1_000).map { SpeedSample(it, 50f) }
        val supported = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE, SignalType.SPEED)

        // when
        val trip = assembler.assemble(
            tripId = "t",
            startedAtMs = 0,
            endedAtMs = 120_000,
            recording = TripRecording(emptyList(), signals, emptyList(), emptyList(), speeds),
            supported = supported,
        )

        // then
        assertEquals(listOf(TripEvent.SignalLeftOnEvent(id = "event-1", occurredAtMs = 10_000, durationSec = 40)), trip.events)
    }

    @Test
    fun `조립한 회전 이벤트에 최고 속도를 담는다`() {
        // given
        // 핸들을 40s에 꺾기 시작해 42s에 100도, 45s에 풀어 왼쪽 회전 40s ~ 45s
        val steering = listOf(
            SteeringSample(0, 0f),
            SteeringSample(40_000, 30f),
            SteeringSample(42_000, 100f),
            SteeringSample(45_000, 0f),
        )
        // 회전 시작 때 35km/h(39.5s 샘플), 회전 중 32km/h, 회전 뒤 50km/h
        val speeds = listOf(SpeedSample(39_500, 35f), SpeedSample(41_000, 32f), SpeedSample(46_000, 50f))

        // when
        val trip = assembler.assemble(
            tripId = "t",
            startedAtMs = 0,
            endedAtMs = 60_000,
            recording = TripRecording(steering, emptyList(), emptyList(), emptyList(), speeds),
            supported = all,
        )

        // then
        val turn = trip.events.filterIsInstance<TripEvent.TurnEvent>().single()
        assertEquals(35.0, turn.maxSpeedKmh)
    }
}
