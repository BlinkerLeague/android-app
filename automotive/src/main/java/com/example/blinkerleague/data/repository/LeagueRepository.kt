package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.LeagueApi
import com.example.blinkerleague.domain.model.Championship
import com.example.blinkerleague.domain.model.League
import com.example.blinkerleague.domain.model.LeagueEntry
import com.example.blinkerleague.domain.model.LeagueRegion
import com.example.blinkerleague.domain.model.MyLeague
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/** 지역 리그. 받지 못하면 null */
class LeagueRepository(
    private val api: LeagueApi,
    /** 내 닉네임. 서버 순위 목록은 닉네임만 주므로 내 행을 닉네임으로 찾음 */
    private val myNickname: () -> String? = { null },
) {
    suspend fun current(): League? {
        val body = (api.currentLeague() as? ApiResult.Success)?.body ?: return null
        val endsAt = runCatching { OffsetDateTime.parse(body.season.endsAt).toInstant().toEpochMilli() }.getOrNull() ?: return null
        val me = body.me
        val nickname = myNickname()
        return League(
            seasonId = body.season.seasonId,
            seasonEndsAtMs = endsAt,
            myLeague = body.league?.let { l ->
                MyLeague(
                    name = l.name ?: l.leagueCode,
                    participants = l.participants,
                    rankPublic = l.rankingPublished,
                    myRank = me?.rank,
                    myScore = me?.score?.roundToInt(),
                    qualified = me?.eligible ?: false,
                    seasonKm = me?.distanceKm ?: 0.0,
                    seasonTrips = me?.trips ?: 0,
                    top = l.rankings.map { LeagueEntry(it.rank, it.nickname, it.score.roundToInt(), me = nickname != null && it.nickname == nickname) },
                    averageScore = me?.averageScore?.roundToInt(),
                    points = me?.points ?: 0,
                )
            },
            regions = body.regions.map { LeagueRegion(it.name ?: it.leagueCode, it.safetyIndex?.roundToInt(), it.rank) },
            championships = me?.championships.orEmpty().map { Championship(it.seasonId, it.name ?: it.leagueCode) },
        )
    }
}
