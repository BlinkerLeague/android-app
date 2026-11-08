package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.MissionConditionDto
import com.example.blinkerleague.data.remote.dto.MissionProgressDto
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.data.remote.dto.TodayMissionResponse
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 개발·시연용 미션 API. 서버 plan.md 3.6 "회전 깜빡이 완벽"의 **라운드 단위 판정**(서버 결정 D25, 11.08)을 흉내 냄:
 * - 운행 1건 = 1라운드. 조건(회전 [requiredTurns]회 이상, 모두 깜빡이 켬)은 라운드 하나 안에서 판정 (하루 합산 안 함)
 * - 그날(운행 종료 시각 기준) 라운드 하나라도 채우면 ACHIEVED. 실패한 라운드가 있어도 그날은 IN_PROGRESS
 * - 진행도: 달성했으면 달성한 라운드, 아니면 가장 최근 라운드. 짧은 운행(EXCLUDED_SHORT)은 제외
 * - 오늘 운행이 없으면 마지막 미션 날의 최종 상태(ACHIEVED, 못 채웠으면 FAILED)
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

        // 라운드 = 점수 대상 운행 (turns 합계, 성공 수), 종료 시각순
        val rounds = tripApi.uploadedTrips()
            .filter { (_, result) -> result.status != ScoreStatus.EXCLUDED_SHORT }
            .map { (request, result) ->
                Round(
                    day = OffsetDateTime.parse(request.endedAt).atZoneSameInstant(zone).toLocalDate(),
                    endedAt = OffsetDateTime.parse(request.endedAt).toInstant(),
                    turns = result.turns?.total ?: 0,
                    success = result.turns?.success ?: 0,
                )
            }
            .sortedBy { it.endedAt }
        // 오늘 라운드가 없으면 마지막 미션 날
        val day = if (rounds.any { it.day == today }) today else rounds.lastOrNull()?.day ?: today
        val dayRounds = rounds.filter { it.day == day }
        val achieved = dayRounds.firstOrNull { it.achieved(requiredTurns) }
        val shown = achieved ?: dayRounds.lastOrNull()
        val turns = shown?.turns ?: 0
        val success = shown?.success ?: 0
        val status = when {
            achieved != null -> "ACHIEVED"
            day != today -> "FAILED"
            else -> "IN_PROGRESS"
        }
        return ApiResult.Success(
            TodayMissionResponse(
                missionId = 1,
                title = "회전 깜빡이 완벽",
                periodKey = day.toString(),
                status = status,
                progress = MissionProgressDto(turns, success, requiredTurns),
                rewardPoint = 20,
                conditions = listOf(
                    MissionConditionDto("TURN_COUNT", turns, requiredTurns),
                    MissionConditionDto("SIGNAL_RATE", if (turns == 0) 0 else success * 100 / turns, 100),
                ),
            ),
        )
    }

    private data class Round(val day: LocalDate, val endedAt: Instant, val turns: Int, val success: Int) {
        fun achieved(required: Int) = turns >= required && success == turns
    }
}
