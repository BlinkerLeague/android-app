package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.MetricDto
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.data.remote.dto.TripEventType
import com.example.blinkerleague.data.remote.dto.TripScoreResponse
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import com.example.blinkerleague.data.remote.dto.TurnCountDto
import com.example.blinkerleague.data.remote.dto.TurnPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.math.roundToInt

/**
 * 서버가 나오기 전 개발·시연용 API. 서버 plan.md 3.4·3.5의 규칙을 흉내 냄:
 * 1km 미만은 EXCLUDED_SHORT, 회전 0회는 NO_TURN, 나머지는 깜빡이 사용률 × 100.
 * 같은 tripId는 처음 결과를 그대로 돌려줌.
 *
 * @param failWith null이 아니면 모든 요청을 이 결과로 실패시킴 (재전송 테스트용)
 */
class FakeTripApi(
    var failWith: ApiResult<Nothing>? = null,
    private val minDistanceKm: Double = 1.0,
) : TripApi {

    private val json = Json { ignoreUnknownKeys = true }
    private val stored = mutableMapOf<String, TripScoreResponse>()
    val received = mutableListOf<TripUploadRequest>()

    override suspend fun uploadTrip(request: TripUploadRequest): ApiResult<TripScoreResponse> {
        failWith?.let { return it }
        received += request
        return ApiResult.Success(stored.getOrPut(request.tripId) { score(request) })
    }

    private fun score(request: TripUploadRequest): TripScoreResponse {
        val turns = request.events
            .filter { it.type == TripEventType.TURN }
            .map { json.decodeFromJsonElement<TurnPayload>(it.payload) }
        val success = turns.count { it.signalOn }
        val counts = TurnCountDto(total = turns.size, success = success)

        if (request.distanceKm < minDistanceKm) {
            return TripScoreResponse(request.tripId, ScoreStatus.EXCLUDED_SHORT, turns = counts)
        }
        if (turns.isEmpty()) {
            return TripScoreResponse(request.tripId, ScoreStatus.NO_TURN, turns = counts)
        }
        val ratio = success.toDouble() / turns.size
        val score = (ratio * 100).roundToInt()
        return TripScoreResponse(
            tripId = request.tripId,
            status = ScoreStatus.SCORED,
            score = score,
            metrics = listOf(MetricDto("TURN_SIGNAL", ratio, score, 1.0)),
            turns = counts,
        )
    }
}
