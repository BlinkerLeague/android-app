package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.LeagueResponse
import com.example.blinkerleague.data.remote.dto.LeagueSeasonDto
import com.example.blinkerleague.data.remote.dto.LeagueRegionDto
import com.example.blinkerleague.data.remote.dto.MyLeagueDto
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.domain.region.LeagueRegions
import java.time.Instant
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/** 지역 리그 API (서버 4.4, 미구현) */
interface LeagueApi {
    /** `GET /api/leagues/current` (서버 요청 14번 형식) */
    suspend fun currentLeague(): ApiResult<LeagueResponse>
}

/**
 * Fake: 다른 운전자가 없으므로 지어낸 순위를 넣지 않음. 나 혼자인 리그 → 참여자 10명 미만이라 순위 비공개(서버 D11).
 * 시즌은 달력 월(D29), 리그 점수는 이번 달 점수 난 운행의 거리 가중 평균(D5·D19, P0은 깜빡이만 공통 항목),
 * 순위 자격은 시즌 50km 또는 10회(D13)
 */
class FakeLeagueApi(
    private val tripApi: FakeTripApi,
    private val regionCode: () -> String?,
    private val clock: () -> Long,
    private val zone: ZoneId,
) : LeagueApi {

    override suspend fun currentLeague(): ApiResult<LeagueResponse> {
        val month = YearMonth.from(Instant.ofEpochMilli(clock()).atZone(zone))
        val start = month.atDay(1).atStartOfDay(zone)
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone)
        val trips = tripApi.uploadedTrips()
            .filter { (request, result) ->
                val at = OffsetDateTime.parse(request.endedAt).toInstant()
                result.status == ScoreStatus.SCORED && result.score != null && !at.isBefore(start.toInstant()) && at.isBefore(end.toInstant())
            }
        val km = trips.sumOf { it.first.distanceKm }
        val score = if (km > 0) (trips.sumOf { it.first.distanceKm * it.second.score!! } / km).roundToInt() else null
        val name = regionCode()?.let(LeagueRegions::of)
        val my = name?.let {
            MyLeagueDto(
                region = it, name = it, participants = 1, rankPublic = false, myRank = null, myScore = score,
                qualified = km >= 50 || trips.size >= 10, seasonKm = km, seasonTrips = trips.size,
            )
        }
        return ApiResult.Success(
            LeagueResponse(
                season = LeagueSeasonDto(month.toString(), start.toOffsetDateTime().toString(), end.toOffsetDateTime().toString()),
                myLeague = my,
                regions = listOfNotNull(name?.let { LeagueRegionDto(it, it, null, null) }),
            ),
        )
    }
}
