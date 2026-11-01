package com.example.blinkerleague.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// POST /api/trips 형식. 서버 plan.md 3.4·3.5 기준. 시각은 ISO-8601 오프셋 형식(예: 2026-11-03T08:12:00+09:00)
// 원시 위치 좌표 필드는 두지 않음

@Serializable
data class TripUploadRequest(
    val tripId: String,
    val startedAt: String,
    val endedAt: String,
    val distanceKm: Double,
    val supported: List<String>,
    val events: List<TripEventDto>,
)

@Serializable
data class TripEventDto(
    val eventId: String,
    val type: String,
    val occurredAt: String,
    val payload: JsonElement,
)

/** `TURN` 이벤트 payload. 서버와 맞출 항목 (plan.md "서버와 맞춰야 할 항목") */
@Serializable
data class TurnPayload(
    val direction: String,
    val signalOn: Boolean,
    val uTurn: Boolean,
)

/** `POST /api/trips` 응답과 `GET /api/trips/{id}/score` 응답 */
@Serializable
data class TripScoreResponse(
    val tripId: String,
    val status: String,
    val score: Int? = null,
    val metrics: List<MetricDto> = emptyList(),
    val turns: TurnCountDto? = null,
)

@Serializable
data class MetricDto(
    val metric: String,
    val value: Double,
    val score: Int,
    val weight: Double,
)

@Serializable
data class TurnCountDto(
    val total: Int,
    val success: Int,
)

object TripEventType {
    const val TURN = "TURN"
}

object ScoreStatus {
    const val SCORED = "SCORED"
    const val NO_TURN = "NO_TURN"
    const val EXCLUDED_SHORT = "EXCLUDED_SHORT"
}
