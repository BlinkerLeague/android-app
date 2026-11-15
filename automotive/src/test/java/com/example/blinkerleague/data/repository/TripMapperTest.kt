package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.local.TripWithEvents
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.Trip
import com.example.blinkerleague.domain.model.TripEvent
import com.example.blinkerleague.domain.model.TurnDirection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TripMapperTest {

    private val mapper = TripMapper(ZoneId.of("Asia/Seoul"))

    // 2026-11-03T08:00:00+09:00
    private val start = 1_793_660_400_000L

    private val trip = Trip(
        id = "trip-1",
        startedAtMs = start,
        endedAtMs = start + 600_000,
        distanceKm = 3.2,
        supported = setOf(SignalType.TURN_SIGNAL, SignalType.BRAKE),
        events = listOf(
            TripEvent.TurnEvent("e2", start + 300_000, TurnDirection.RIGHT, signalOn = false, uTurn = false),
            TripEvent.TurnEvent("e1", start + 120_000, TurnDirection.LEFT, signalOn = true, uTurn = true),
        ),
    )

    @Test
    fun `업로드 요청 - 시각은 오프셋 포함 ISO-8601, 이벤트는 시각순, payload는 JSON 객체`() {
        val request = mapper.toUploadRequest(TripWithEvents(mapper.toEntity(trip), mapper.toEventEntities(trip)))

        assertEquals("trip-1", request.tripId)
        assertEquals("2026-11-03T08:00:00+09:00", request.startedAt)
        assertEquals("2026-11-03T08:10:00+09:00", request.endedAt)
        // 핸들 각도가 없으면 깜빡이 항목은 측정 불가, 브레이크는 점수 항목이 아님 → 빈 목록
        assertEquals(emptyList<String>(), request.supported)
        assertEquals(listOf("e1", "e2"), request.events.map { it.eventId })

        val first = request.events.first()
        assertEquals("TURN", first.type)
        assertEquals("2026-11-03T08:02:00+09:00", first.occurredAt)
        val payload = first.payload.jsonObject
        assertEquals("LEFT", payload["direction"]!!.jsonPrimitive.content)
        assertEquals(true, payload["signalOn"]!!.jsonPrimitive.boolean)
        assertEquals(true, payload["uTurn"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `유턴 여부를 모르면 payload에서 uTurn을 뺌`() {
        val unknown = trip.copy(
            events = listOf(TripEvent.TurnEvent("e1", start + 1_000, TurnDirection.LEFT, signalOn = true, uTurn = null)),
        )
        val request = mapper.toUploadRequest(TripWithEvents(mapper.toEntity(unknown), mapper.toEventEntities(unknown)))
        assertEquals(setOf("direction", "signalOn"), request.events.single().payload.jsonObject.keys)
    }

    @Test
    fun `supported는 서버 점수 항목 이름 - 방향지시등과 핸들 각도가 있으면 TURN_SIGNAL`() {
        val measurable = trip.copy(supported = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE, SignalType.HEADLIGHT))
        val request = mapper.toUploadRequest(TripWithEvents(mapper.toEntity(measurable), mapper.toEventEntities(measurable)))
        assertEquals(listOf("NIGHT_HEADLIGHT", "TURN_SIGNAL"), request.supported)
    }

    @Test
    fun `지원 신호가 없으면 빈 목록`() {
        val noSignal = trip.copy(supported = emptySet(), events = emptyList())
        val request = mapper.toUploadRequest(TripWithEvents(mapper.toEntity(noSignal), emptyList()))
        assertEquals(emptyList<String>(), request.supported)
    }

    @Test
    fun `SIGNAL_LEFT_ON 이벤트는 type SIGNAL_LEFT_ON, payload {durationSec}로 저장 · 업로드한다`() {
        // given
        // 방향지시등 · 핸들 각도 · 속도를 지원하는 운행에서 깜빡이를 40초 켜 둔 채 잊음
        val leftOn = trip.copy(
            supported = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE, SignalType.SPEED),
            events = listOf(TripEvent.SignalLeftOnEvent("e1", start + 120_000, durationSec = 40)),
        )

        // when
        val request = mapper.toUploadRequest(TripWithEvents(mapper.toEntity(leftOn), mapper.toEventEntities(leftOn)))

        // then
        assertTrue("supported: ${request.supported}", "SIGNAL_CANCEL" in request.supported)
        val event = request.events.single()
        assertEquals("e1", event.eventId)
        assertEquals("SIGNAL_LEFT_ON", event.type)
        assertEquals("2026-11-03T08:02:00+09:00", event.occurredAt)
        assertEquals(Json.parseToJsonElement("""{"durationSec":40}"""), event.payload)
    }

    @Test
    fun `TURN payload에 maxSpeedKmh를 소수 1자리로 넣는다`() {
        // given
        val withSpeed = trip.copy(
            events = listOf(TripEvent.TurnEvent("e1", start + 1_000, TurnDirection.LEFT, signalOn = true, uTurn = null, maxSpeedKmh = 32.46)),
        )

        // when
        val request = mapper.toUploadRequest(TripWithEvents(mapper.toEntity(withSpeed), mapper.toEventEntities(withSpeed)))

        // then
        val payload = request.events.single().payload.jsonObject
        assertEquals(setOf("direction", "signalOn", "maxSpeedKmh"), payload.keys)
        assertEquals(32.5, payload["maxSpeedKmh"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `HARSH_ACCEL · HARSH_BRAKE는 빈 payload {}로 저장 · 업로드한다`() {
        // given
        // 속도 · 브레이크를 지원하는 운행에서 2분에 급가속, 5분에 급감속
        val harsh = trip.copy(
            supported = setOf(SignalType.SPEED, SignalType.BRAKE),
            events = listOf(
                TripEvent.HarshAccelEvent("e1", start + 120_000),
                TripEvent.HarshBrakeEvent("e2", start + 300_000),
            ),
        )

        // when
        val request = mapper.toUploadRequest(TripWithEvents(mapper.toEntity(harsh), mapper.toEventEntities(harsh)))

        // then
        assertTrue("supported: ${request.supported}", "SMOOTH_DRIVING" in request.supported)
        assertEquals(listOf("e1" to "HARSH_ACCEL", "e2" to "HARSH_BRAKE"), request.events.map { it.eventId to it.type })
        assertEquals(listOf(JsonObject(emptyMap()), JsonObject(emptyMap())), request.events.map { it.payload })
    }

    @Test
    fun `NIGHT_SEGMENT는 payload {durationSec, headlightOffSec}로 저장 · 업로드한다`() {
        // given
        // 하향등을 지원하는 운행에서 2분부터 10분 동안 야간 주행, 그중 4분은 하향등이 꺼져 있었음
        val night = trip.copy(
            supported = setOf(SignalType.HEADLIGHT),
            events = listOf(TripEvent.NightSegmentEvent("e1", start + 120_000, durationSec = 600, headlightOffSec = 240)),
        )

        // when
        val request = mapper.toUploadRequest(TripWithEvents(mapper.toEntity(night), mapper.toEventEntities(night)))

        // then
        assertTrue("supported: ${request.supported}", "NIGHT_HEADLIGHT" in request.supported)
        val event = request.events.single()
        assertEquals("e1", event.eventId)
        assertEquals("NIGHT_SEGMENT", event.type)
        assertEquals("2026-11-03T08:02:00+09:00", event.occurredAt)
        assertEquals(Json.parseToJsonElement("""{"durationSec":600,"headlightOffSec":240}"""), event.payload)
    }
}
