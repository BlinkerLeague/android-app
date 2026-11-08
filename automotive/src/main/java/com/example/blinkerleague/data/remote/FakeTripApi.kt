package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.MetricDto
import com.example.blinkerleague.data.remote.dto.PlayDto
import com.example.blinkerleague.data.remote.dto.PlayResult
import com.example.blinkerleague.data.remote.dto.PlaySummaryDto
import com.example.blinkerleague.data.remote.dto.RecentTripDto
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.data.remote.dto.TripEventType
import com.example.blinkerleague.data.remote.dto.TripScoreResponse
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import com.example.blinkerleague.data.remote.dto.TurnCountDto
import com.example.blinkerleague.data.remote.dto.TurnPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/**
 * 서버가 나오기 전 개발·시연용 API. 서버 plan.md 3.4·3.5의 규칙을 흉내 냄:
 * 1km 미만은 EXCLUDED_SHORT, 회전 0회는 NO_TURN, 나머지는 깜빡이 사용률 × 100.
 * 등급은 3.3 `grades`(S95·A85·B70·C50, 그 아래는 null), 득점 = 깜빡이를 켠 회전, 실점 = 안 켠 회전, 반칙 0.
 * 같은 tripId는 처음 결과를 그대로 돌려줌.
 *
 * @param failWith null이 아니면 모든 요청을 이 결과로 실패시킴 (재전송 테스트용)
 */
class FakeTripApi(
    var failWith: ApiResult<Nothing>? = null,
    private val minDistanceKm: Double = 1.0,
    private val grades: List<Pair<String, Int>> = listOf("S" to 95, "A" to 85, "B" to 70, "C" to 50),
) : TripApi {

    private val json = Json { ignoreUnknownKeys = true }
    private val stored = mutableMapOf<String, TripScoreResponse>()
    val received = mutableListOf<TripUploadRequest>()

    override suspend fun uploadTrip(request: TripUploadRequest): ApiResult<TripScoreResponse> {
        failWith?.let { return it }
        received += request
        return ApiResult.Success(stored.getOrPut(request.tripId) { score(request) })
    }

    override suspend fun getScore(tripId: String): ApiResult<TripScoreResponse> {
        failWith?.let { return it }
        return stored[tripId]?.let { ApiResult.Success(it) } ?: ApiResult.HttpError(404)
    }

    override suspend fun getRecentTrips(limit: Int): ApiResult<List<RecentTripDto>> {
        failWith?.let { return it }
        val recent = uploadedTrips()
            .sortedByDescending { (request, _) -> OffsetDateTime.parse(request.endedAt).toInstant() }
            .take(limit)
            .map { (request, result) ->
                RecentTripDto(request.tripId, request.endedAt, request.distanceKm, result.status, result.score, result.grade)
            }
        return ApiResult.Success(recent)
    }

    /** 지금까지 올라온 운행과 그 결과 (tripId당 하나, 처음 받은 요청 기준) */
    fun uploadedTrips(): List<Pair<TripUploadRequest, TripScoreResponse>> =
        received.distinctBy { it.tripId }.map { it to stored.getValue(it.tripId) }

    private fun score(request: TripUploadRequest): TripScoreResponse {
        val turnEvents = request.events
            .filter { it.type == TripEventType.TURN }
            .sortedBy { OffsetDateTime.parse(it.occurredAt).toInstant() }
        val turns = turnEvents.map { json.decodeFromJsonElement<TurnPayload>(it.payload) }
        val success = turns.count { it.signalOn }
        val counts = TurnCountDto(total = turns.size, success = success)
        val summary = PlaySummaryDto(goals = success, conceded = turns.size - success)
        val plays = turnEvents.zip(turns) { event, turn ->
            PlayDto(event.occurredAt, TripEventType.TURN, turn.direction, if (turn.signalOn) PlayResult.GOAL else PlayResult.CONCEDED)
        }
        val base = TripScoreResponse(request.tripId, ScoreStatus.SCORED, turns = counts, summary = summary, plays = plays)

        if (request.distanceKm < minDistanceKm) return base.copy(status = ScoreStatus.EXCLUDED_SHORT)
        if (turns.isEmpty()) return base.copy(status = ScoreStatus.NO_TURN)

        val ratio = success.toDouble() / turns.size
        val score = (ratio * 100).roundToInt()
        return base.copy(
            score = score,
            grade = grades.firstOrNull { (_, min) -> score >= min }?.first,
            metrics = listOf(MetricDto("TURN_SIGNAL", ratio, score, 1.0)),
        )
    }
}
