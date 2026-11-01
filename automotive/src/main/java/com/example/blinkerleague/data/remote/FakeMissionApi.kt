package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.MissionProgressDto
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.data.remote.dto.TodayMissionResponse
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 서버가 나오기 전 개발·시연용 미션 API. 서버 plan.md 3.6의 "회전 깜빡이 완벽" 규칙을 흉내 냄:
 * 그날 점수 대상 운행(1km 미만 제외)의 회전을 합산해서
 * 실패 회전이 하나라도 있으면 FAILED, 회전 [requiredTurns]회 이상 모두 성공이면 ACHIEVED, 아니면 IN_PROGRESS.
 *
 * 운행 기록은 [tripApi]에 올라간 것을 그대로 씀.
 */
class FakeMissionApi(
    private val tripApi: FakeTripApi,
    private val clock: () -> Long,
    private val zone: ZoneId,
    private val requiredTurns: Int = 3,
) : MissionApi {

    var failWith: ApiResult<Nothing>? = null

    override suspend fun todayMission(): ApiResult<TodayMissionResponse> {
        failWith?.let { return it }
        val today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

        val todays = tripApi.uploadedTrips().filter { (request, result) ->
            OffsetDateTime.parse(request.startedAt).atZoneSameInstant(zone).toLocalDate() == today &&
                result.status != ScoreStatus.EXCLUDED_SHORT
        }
        val turns = todays.sumOf { it.second.turns?.total ?: 0 }
        val success = todays.sumOf { it.second.turns?.success ?: 0 }
        val status = when {
            turns > success -> "FAILED"
            turns >= requiredTurns -> "ACHIEVED"
            else -> "IN_PROGRESS"
        }
        return ApiResult.Success(
            TodayMissionResponse(
                missionId = 1,
                title = "회전 깜빡이 완벽",
                periodKey = today.toString(),
                status = status,
                progress = MissionProgressDto(turns, success, requiredTurns),
            ),
        )
    }
}
