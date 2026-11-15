package com.example.blinkerleague.data.repository

import com.example.blinkerleague.domain.model.ScoreItem
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.data.local.TripEntity
import com.example.blinkerleague.data.local.TripEventEntity
import com.example.blinkerleague.data.local.TripWithEvents
import com.example.blinkerleague.data.local.UploadState
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.data.remote.dto.SignalLeftOnPayload
import com.example.blinkerleague.data.remote.dto.TripEventDto
import com.example.blinkerleague.data.remote.dto.TripEventType
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import com.example.blinkerleague.data.remote.dto.TurnPayload
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.Trip
import com.example.blinkerleague.domain.model.TripEvent
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnRecord
import kotlinx.serialization.decodeFromString
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
            is TripEvent.SignalLeftOnEvent -> TripEventEntity(
                eventId = event.id,
                tripId = trip.id,
                type = TripEventType.SIGNAL_LEFT_ON,
                occurredAtMs = event.occurredAtMs,
                payload = json.encodeToString(SignalLeftOnPayload(event.durationSec)),
            )
        }
    }

    fun toUploadRequest(stored: TripWithEvents) = TripUploadRequest(
        tripId = stored.trip.tripId,
        startedAt = isoTime(stored.trip.startedAtMs),
        endedAt = isoTime(stored.trip.endedAtMs),
        distanceKm = stored.trip.distanceKm,
        // 서버는 신호 이름이 아니라 측정 가능한 점수 항목(ScoreItem)을 받음. 모르는 이름이면 400으로 거부됨
        supported = ScoreItem.measurable(
            stored.trip.supported.split(",").mapNotNull { name -> SignalType.entries.firstOrNull { it.name == name } }.toSet(),
        ).map { it.name }.sorted(),
        events = stored.events.sortedBy { it.occurredAtMs }.map {
            TripEventDto(
                eventId = it.eventId,
                type = it.type,
                occurredAt = isoTime(it.occurredAtMs),
                payload = json.parseToJsonElement(it.payload),
            )
        },
    )

    fun toResult(stored: TripWithEvents): TripResult {
        val trip = stored.trip
        val scoreState = when {
            trip.uploadState == UploadState.REJECTED -> ScoreState.REJECTED
            trip.uploadState == UploadState.PENDING -> ScoreState.PENDING
            trip.scoreStatus == ScoreStatus.SCORED -> ScoreState.SCORED
            trip.scoreStatus == ScoreStatus.NO_TURN -> ScoreState.NO_TURN
            trip.scoreStatus == ScoreStatus.EXCLUDED_SHORT -> ScoreState.EXCLUDED_SHORT
            else -> ScoreState.PENDING
        }
        val turns = stored.events
            .filter { it.type == TripEventType.TURN }
            .sortedBy { it.occurredAtMs }
            .map {
                val payload = lenientJson.decodeFromString<TurnPayload>(it.payload)
                TurnRecord(it.occurredAtMs, TurnDirection.valueOf(payload.direction), payload.signalOn)
            }
        return TripResult(
            tripId = trip.tripId,
            startedAtMs = trip.startedAtMs,
            endedAtMs = trip.endedAtMs,
            distanceKm = trip.distanceKm,
            scoreState = scoreState,
            score = trip.score,
            turns = turns,
            supported = trip.supported.split(",").mapNotNull { name -> SignalType.entries.firstOrNull { it.name == name } }.toSet(),
        )
    }

    private val lenientJson = Json { ignoreUnknownKeys = true }

    /** 초 단위까지, 오프셋 포함 (예: 2026-11-03T08:00:00+09:00) */
    private fun isoTime(epochMs: Long): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
