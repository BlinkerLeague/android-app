package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.data.scenario.Scenarios
import com.example.blinkerleague.domain.config.Coordinate
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.config.SafetyWarningConfig
import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.HeadlightSample
import com.example.blinkerleague.domain.model.SeatBeltSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.TelltaleSample
import com.example.blinkerleague.domain.model.TripEvent
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnSignalState
import com.example.blinkerleague.domain.model.VehicleStateSample
import com.example.blinkerleague.domain.model.WiperSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

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

    @Test
    fun `속도 · 브레이크가 지원되면 HARSH_ACCEL과 HARSH_BRAKE 이벤트를 만든다`() {
        // given
        // 운행 0s ~ 60s, 1초 간격 속도. 10s까지 30km/h → 11s 45km/h(급가속) → 30s까지 45km/h → 31s 30km/h
        // 브레이크는 30.5s에 밟고 32s에 뗌 → 31s 급감속 (기본 기준 1초에 10km/h)
        val speeds = (0L..60_000L step 1_000).map { t ->
            val kmh = when {
                t <= 10_000 -> 30f
                t <= 30_000 -> 45f
                else -> 30f
            }
            SpeedSample(t, kmh)
        }
        val brakes = listOf(BrakeSample(30_500, pressed = true), BrakeSample(32_000, pressed = false))
        val supported = setOf(SignalType.SPEED, SignalType.BRAKE)

        // when
        val trip = assembler.assemble(
            tripId = "t",
            startedAtMs = 0,
            endedAtMs = 60_000,
            recording = TripRecording(emptyList(), emptyList(), emptyList(), emptyList(), speeds, brakes),
            supported = supported,
        )

        // then
        assertEquals(
            listOf(
                TripEvent.HarshAccelEvent(id = "event-1", occurredAtMs = 11_000),
                TripEvent.HarshBrakeEvent(id = "event-2", occurredAtMs = 31_000),
            ),
            trip.events,
        )
    }

    @Test
    fun `하향등이 지원되고 대표 좌표가 있으면 NIGHT_SEGMENT를 만든다`() {
        // given
        // 서울 대표 좌표, Asia/Seoul, 2026-11-15 22:00 ~ 22:10 KST 운행 (운행 전체가 밤)
        // 하향등: 21:59 꺼짐 → 22:02 켜짐 → 22:08 꺼짐. 꺼진 시간 240초
        val zone = ZoneId.of("Asia/Seoul")
        fun kst(hour: Int, minute: Int) =
            ZonedDateTime.of(2026, 11, 15, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
        val seoul = Coordinate(37.57, 126.98)
        val headlights = listOf(
            HeadlightSample(kst(21, 59), on = false),
            HeadlightSample(kst(22, 2), on = true),
            HeadlightSample(kst(22, 8), on = false),
        )

        // when
        val trip = assembler.assemble(
            tripId = "t",
            startedAtMs = kst(22, 0),
            endedAtMs = kst(22, 10),
            recording = TripRecording(emptyList(), emptyList(), emptyList(), emptyList(), headlights = headlights),
            supported = setOf(SignalType.HEADLIGHT),
            coordinate = seoul,
            zone = zone,
        )

        // then
        assertEquals(
            listOf(TripEvent.NightSegmentEvent(id = "event-1", occurredAtMs = kst(22, 0), durationSec = 600, headlightOffSec = 240)),
            trip.events,
        )
    }

    @Test
    fun `운행당 SEAT_BELT 이벤트 하나를 출발 시각에 만든다`() {
        // given
        // 운행 0s ~ 60s. 0s 기어 P → 5s D로 출발, 출발부터 1초 간격으로 30km/h
        // 벨트: 운행 전 -1s에 착용, 운행 내내 그대로
        val vehicleStates = listOf(
            VehicleStateSample(0, ignitionOn = null, gear = Gear.P),
            VehicleStateSample(5_000, ignitionOn = null, gear = Gear.D),
        )
        val speeds = (5_000L..60_000L step 1_000).map { SpeedSample(it, 30f) }
        val seatBelts = listOf(SeatBeltSample(-1_000, buckled = true))

        // when
        val trip = assembler.assemble(
            tripId = "t",
            startedAtMs = 0,
            endedAtMs = 60_000,
            recording = TripRecording(emptyList(), emptyList(), vehicleStates, emptyList(), speeds = speeds, seatBelts = seatBelts),
            supported = setOf(SignalType.SEAT_BELT, SignalType.SPEED),
        )

        // then
        assertEquals(
            listOf(TripEvent.SeatBeltEvent(id = "event-1", occurredAtMs = 5_000, buckledAtDeparture = true, unbuckledMovingSec = 0)),
            trip.events,
        )
    }

    @Test
    fun `출발 시점 벨트 상태를 모르는 운행은 supported에서 SEAT_BELT를 뺀다`() {
        // given
        // 운행 0s ~ 60s. 0s 기어 P → 5s D로 출발, 출발부터 1초 간격으로 30km/h
        // 벨트 샘플은 하나도 받지 못함
        val vehicleStates = listOf(
            VehicleStateSample(0, ignitionOn = null, gear = Gear.P),
            VehicleStateSample(5_000, ignitionOn = null, gear = Gear.D),
        )
        val speeds = (5_000L..60_000L step 1_000).map { SpeedSample(it, 30f) }

        // when
        val trip = assembler.assemble(
            tripId = "t",
            startedAtMs = 0,
            endedAtMs = 60_000,
            recording = TripRecording(emptyList(), emptyList(), vehicleStates, emptyList(), speeds = speeds),
            supported = setOf(SignalType.SEAT_BELT, SignalType.SPEED),
        )

        // then
        assertEquals(setOf(SignalType.SPEED), trip.supported)
        assertEquals(emptyList<TripEvent>(), trip.events)
    }

    @Test
    fun `경고등과 속도가 지원되면 SAFETY_WARNING 이벤트를 만든다`() {
        // given
        // 대상 경고등은 ABS (기본 설정의 목록은 비어 있어서 테스트에서 주입)
        val safetyAssembler = TripAssembler(
            DetectionConfig.DEFAULT.copy(safetyWarning = SafetyWarningConfig(telltaleTypes = setOf("ABS"))),
        ) { "event-${++seq}" }
        // 운행 0s ~ 60s, 1초 간격으로 30km/h. 경고등은 1초 폴링, 20s ~ 29s 동안 ABS 켜짐
        val speeds = (0L..60_000L step 1_000).map { SpeedSample(it, 30f) }
        val telltales = (0L..60_000L step 1_000).map { t ->
            TelltaleSample(t, if (t in 20_000L..29_000L) setOf("ABS") else emptySet())
        }

        // when
        val trip = safetyAssembler.assemble(
            tripId = "t",
            startedAtMs = 0,
            endedAtMs = 60_000,
            recording = TripRecording(emptyList(), emptyList(), emptyList(), emptyList(), speeds = speeds, telltales = telltales),
            supported = setOf(SignalType.SAFETY_WARNING, SignalType.SPEED),
        )

        // then
        assertEquals(listOf(TripEvent.SafetyWarningEvent(id = "event-1", occurredAtMs = 20_000)), trip.events)
    }

    @Test
    fun `와이퍼와 하향등이 지원되면 RAIN_SEGMENT 이벤트를 만든다`() {
        // given
        // 운행 0s ~ 300s. 대표 좌표를 넘기지 않아 야간 구간은 만들지 않음
        // 와이퍼 60s 켜짐 → 180s 꺼짐 (120초, 기준 30초 초과). 하향등은 계속 꺼짐
        val wipers = listOf(
            WiperSample(60_000, on = true),
            WiperSample(180_000, on = false),
        )
        val headlights = listOf(HeadlightSample(0, on = false))

        // when
        val trip = assembler.assemble(
            tripId = "t",
            startedAtMs = 0,
            endedAtMs = 300_000,
            recording = TripRecording(emptyList(), emptyList(), emptyList(), emptyList(), headlights = headlights, wipers = wipers),
            supported = setOf(SignalType.WIPER, SignalType.HEADLIGHT),
        )

        // then
        assertEquals(
            listOf(
                TripEvent.RainSegmentEvent(
                    id = "event-1",
                    occurredAtMs = 60_000,
                    durationSec = 120,
                    headlightOffSec = 120,
                    knownLimitSec = 0,
                    overRainLimitSec = 0,
                ),
            ),
            trip.events,
        )
    }

    @Test
    fun `이벤트가 maxEvents를 넘으면 TURN은 남기고 횟수형 이벤트를 늦은 것부터 자른다`() {
        // given
        // 이벤트 상한 3개, 대상 경고등 ABS
        val limited = DetectionConfig.DEFAULT.copy(
            trip = DetectionConfig.DEFAULT.trip.copy(maxEvents = 3),
            safetyWarning = SafetyWarningConfig(telltaleTypes = setOf("ABS")),
        )
        val limitedAssembler = TripAssembler(limited) { "event-${++seq}" }
        // 운행 0s ~ 150s, 1초 간격으로 20km/h 일정 (급가감속 · 깜빡이 끄기 판정 없음)
        val speeds = (0L..150_000L step 1_000).map { SpeedSample(it, 20f) }
        // 핸들: 40s ~ 45s, 100s ~ 105s 두 번 왼쪽으로 100도 꺾음 → 회전 2개
        val steering = listOf(
            SteeringSample(0, 0f),
            SteeringSample(40_000, 30f),
            SteeringSample(42_000, 100f),
            SteeringSample(45_000, 0f),
            SteeringSample(100_000, 30f),
            SteeringSample(102_000, 100f),
            SteeringSample(105_000, 0f),
        )
        // 경고등 1초 폴링: ABS가 10s · 60s · 80s · 120s부터 5초씩 켜짐 → 안전 경고 4건
        val abs = listOf(10_000L, 60_000L, 80_000L, 120_000L)
        val telltales = (0L..150_000L step 1_000).map { t ->
            TelltaleSample(t, if (abs.any { t in it until it + 5_000 }) setOf("ABS") else emptySet())
        }
        val supported = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE, SignalType.SPEED, SignalType.SAFETY_WARNING)

        // when
        val trip = limitedAssembler.assemble(
            tripId = "t",
            startedAtMs = 0,
            endedAtMs = 150_000,
            recording = TripRecording(steering, emptyList(), emptyList(), emptyList(), speeds = speeds, telltales = telltales),
            supported = supported,
        )

        // then
        // 회전 2개는 남기고 안전 경고는 가장 이른 10s 1건만 (시각 순)
        val turns = trip.events.filterIsInstance<TripEvent.TurnEvent>()
        assertEquals(2, turns.size)
        assertEquals(
            listOf(TripEvent.SafetyWarningEvent(id = "event-3", occurredAtMs = 10_000)) + turns,
            trip.events,
        )
    }

    @Test
    fun `횟수형 이벤트를 모두 빼도 maxEvents를 넘으면 시각이 이른 것부터 maxEvents개만 남긴다`() {
        // given
        // 이벤트 상한 1개
        val limitedAssembler = TripAssembler(
            DetectionConfig.DEFAULT.copy(trip = DetectionConfig.DEFAULT.trip.copy(maxEvents = 1)),
        ) { "event-${++seq}" }
        // 운행 0s ~ 150s, 1초 간격으로 20km/h 일정. 횟수형 이벤트(급가감속 · 경고등)는 없음
        val speeds = (0L..150_000L step 1_000).map { SpeedSample(it, 20f) }
        // 핸들: 40s ~ 45s, 100s ~ 105s 두 번 왼쪽으로 100도 꺾음 → 회전 2개
        val steering = listOf(
            SteeringSample(0, 0f),
            SteeringSample(40_000, 30f),
            SteeringSample(42_000, 100f),
            SteeringSample(45_000, 0f),
            SteeringSample(100_000, 30f),
            SteeringSample(102_000, 100f),
            SteeringSample(105_000, 0f),
        )

        // when
        val trip = limitedAssembler.assemble(
            tripId = "t",
            startedAtMs = 0,
            endedAtMs = 150_000,
            recording = TripRecording(steering, emptyList(), emptyList(), emptyList(), speeds = speeds),
            supported = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE, SignalType.SPEED),
        )

        // then
        // 40s 회전 1개만
        assertEquals(listOf("event-1" to 40_000L), trip.events.map { it.id to it.occurredAtMs })
    }

    @Test
    fun `기록 샘플이 하나도 없는 신호는 supported에서 뺀다`() {
        // given
        // 운행 0s ~ 60s. 속도 · 하향등은 샘플이 있고, 빠진 신호는 샘플 0개
        val speeds = (0L..60_000L step 1_000).map { SpeedSample(it, 30f) }
        val headlights = listOf(HeadlightSample(0, on = true))
        // 40s ~ 45s 왼쪽 회전
        val steering = listOf(
            SteeringSample(0, 0f),
            SteeringSample(40_000, 30f),
            SteeringSample(42_000, 100f),
            SteeringSample(45_000, 0f),
        )
        fun supportedOf(recording: TripRecording, supported: Set<SignalType>, coordinate: Coordinate? = null) =
            assembler.assemble(
                tripId = "t",
                startedAtMs = 0,
                endedAtMs = 60_000,
                recording = recording,
                supported = supported,
                coordinate = coordinate,
            ).supported

        // when
        val results = listOf(
            // a) 브레이크 샘플 없음
            supportedOf(
                TripRecording(emptyList(), emptyList(), emptyList(), emptyList(), speeds = speeds),
                setOf(SignalType.SPEED, SignalType.BRAKE),
            ),
            // b) 하향등 샘플 없음 (서울 대표 좌표)
            supportedOf(
                TripRecording(emptyList(), emptyList(), emptyList(), emptyList()),
                setOf(SignalType.HEADLIGHT),
                coordinate = Coordinate(37.57, 126.98),
            ),
            // c) 경고등 샘플 없음
            supportedOf(
                TripRecording(emptyList(), emptyList(), emptyList(), emptyList(), speeds = speeds),
                setOf(SignalType.SAFETY_WARNING, SignalType.SPEED),
            ),
            // d) 와이퍼 샘플 없음
            supportedOf(
                TripRecording(emptyList(), emptyList(), emptyList(), emptyList(), headlights = headlights),
                setOf(SignalType.WIPER, SignalType.HEADLIGHT),
            ),
            // e) 회전은 있지만 속도 샘플 없음
            supportedOf(
                TripRecording(steering, emptyList(), emptyList(), emptyList()),
                setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE, SignalType.SPEED),
            ),
        )

        // then
        assertEquals(
            listOf(
                setOf(SignalType.SPEED),
                emptySet(),
                setOf(SignalType.SPEED),
                setOf(SignalType.HEADLIGHT),
                setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE),
            ),
            results,
        )
    }
}
