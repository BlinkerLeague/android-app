package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.local.TripEntity
import com.example.blinkerleague.data.local.TripEventEntity
import com.example.blinkerleague.data.local.TripWithEvents
import com.example.blinkerleague.data.remote.dto.TripEventDto
import com.example.blinkerleague.data.remote.dto.TripEventType
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import com.example.blinkerleague.data.remote.dto.TurnPayload
import com.example.blinkerleague.domain.model.Trip
import com.example.blinkerleague.domain.model.TripEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 도메인 [Trip] ↔ Room 엔티티 ↔ 업로드 DTO 변환 */
class TripMapper(
    private val zone: ZoneId,
    private val json: Json = Json,
) {
    fun toEntity(trip: Trip) = TripEntity(
        tripId = trip.id,
        startedAtMs = trip.startedAtMs,
        endedAtMs = trip.endedAtMs,
        distanceKm = trip.distanceKm,
        supported = trip.supported.map { it.name }.sorted().joinToString(","),
    )

    fun toEventEntities(trip: Trip): List<TripEventEntity> = trip.events.map { event ->
        when (event) {
            is TripEvent.TurnEvent -> TripEventEntity(
                eventId = event.id,
                tripId = trip.id,
                type = TripEventType.TURN,
                occurredAtMs = event.occurredAtMs,
                payload = json.encodeToString(TurnPayload(event.direction.name, event.signalOn, event.uTurn)),
            )
        }
    }

    fun toUploadRequest(stored: TripWithEvents) = TripUploadRequest(
        tripId = stored.trip.tripId,
        startedAt = isoTime(stored.trip.startedAtMs),
        endedAt = isoTime(stored.trip.endedAtMs),
        distanceKm = stored.trip.distanceKm,
        supported = stored.trip.supported.split(",").filter { it.isNotBlank() },
        events = stored.events.sortedBy { it.occurredAtMs }.map {
            TripEventDto(
                eventId = it.eventId,
                type = it.type,
                occurredAt = isoTime(it.occurredAtMs),
                payload = json.parseToJsonElement(it.payload),
            )
        },
    )

    /** 초 단위까지, 오프셋 포함 (예: 2026-11-03T08:00:00+09:00) */
    private fun isoTime(epochMs: Long): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
