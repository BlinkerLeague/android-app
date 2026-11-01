package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.local.TripWithEvents
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.Trip
import com.example.blinkerleague.domain.model.TripEvent
import com.example.blinkerleague.domain.model.TurnDirection
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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
        assertEquals(listOf("BRAKE", "TURN_SIGNAL"), request.supported)
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
    fun `지원 신호가 없으면 빈 목록`() {
        val noSignal = trip.copy(supported = emptySet(), events = emptyList())
        val request = mapper.toUploadRequest(TripWithEvents(mapper.toEntity(noSignal), emptyList()))
        assertEquals(emptyList<String>(), request.supported)
    }
}
