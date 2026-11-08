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
    /** 측정 가능한 점수 항목 이름 ([com.example.blinkerleague.domain.model.ScoreItem]) */
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

/**
 * `TURN` 이벤트 payload. 서버와 맞출 항목 (plan.md "서버와 맞춰야 할 항목").
 * [uTurn]은 알 수 없으면(핸들 감지) null이고, 기본값과 같으므로 JSON에서 빠짐
 */
@Serializable
data class TurnPayload(
    val direction: String,
    val signalOn: Boolean,
    val uTurn: Boolean? = null,
)

/**
 * `POST /api/trips` 응답과 `GET /api/trips/{id}/score` 응답 (서버 plan.md 3.5 `TripResult`).
 * 서버가 아직 안 주는 필드도 있으므로 [score] 이후는 모두 선택값
 */
@Serializable
data class TripScoreResponse(
    val tripId: String,
    val status: String,
    val score: Int? = null,
    /** 등급(S·A·B·C). 점수가 없거나 50점 미만이면 null */
    val grade: String? = null,
    val metrics: List<MetricDto> = emptyList(),
    val turns: TurnCountDto? = null,
    /** 득점·실점·반칙 집계 (SCR-02). 화면은 반칙을 쓰지 않음(screen-plan 9장) */
    val summary: PlaySummaryDto? = null,
    /** 시각순 플레이 (SCR-02a) */
    val plays: List<PlayDto> = emptyList(),
    /** 이 운행을 반영한 그날 미션 판정. 서버 `daily-mission` 전에는 null */
    val mission: TripMissionDto? = null,
)

@Serializable
data class PlaySummaryDto(
    val goals: Int,
    val conceded: Int,
    val fouls: Int = 0,
)

@Serializable
data class PlayDto(
    val occurredAt: String,
    /** 이벤트 종류 ([TripEventType]) */
    val type: String,
    /** 회전 방향(LEFT/RIGHT). 서버 요청 8번, 서버가 안 주면 null */
    val direction: String? = null,
    /** [PlayResult] */
    val result: String,
)

@Serializable
data class TripMissionDto(
    val missionId: Long,
    val status: String,
)

/** `GET /api/trips?limit=N` 항목 (SCR-01 최근 경기). 종료 시각 최신순 */
@Serializable
data class RecentTripDto(
    val tripId: String,
    val endedAt: String,
    val distanceKm: Double,
    val status: String,
    val score: Int? = null,
    val grade: String? = null,
)

object PlayResult {
    const val GOAL = "GOAL"
    const val CONCEDED = "CONCEDED"
}

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
    /** 서버가 받았지만 아직 점수 계산 전 (서버 `TripStatus.UPLOADED`). 화면은 "결과 집계 중" */
    const val UPLOADED = "UPLOADED"
    const val SCORED = "SCORED"
    const val NO_TURN = "NO_TURN"
    const val EXCLUDED_SHORT = "EXCLUDED_SHORT"
}
