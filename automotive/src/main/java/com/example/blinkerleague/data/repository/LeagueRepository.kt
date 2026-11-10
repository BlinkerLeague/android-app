package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.LeagueApi
import com.example.blinkerleague.domain.model.League
import com.example.blinkerleague.domain.model.LeagueEntry
import com.example.blinkerleague.domain.model.LeagueRegion
import com.example.blinkerleague.domain.model.MyLeague
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/** 지역 리그. 받지 못하면 null */
class LeagueRepository(private val api: LeagueApi) {
    suspend fun current(): League? {
        val body = (api.currentLeague() as? ApiResult.Success)?.body ?: return null
        val endsAt = runCatching { OffsetDateTime.parse(body.season.endsAt).toInstant().toEpochMilli() }.getOrNull() ?: return null
        val me = body.me
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
                    top = l.rankings.map { LeagueEntry(it.rank, it.nickname, it.score.roundToInt(), false) },
                )
            },
            regions = body.regions.map { LeagueRegion(it.name ?: it.leagueCode, it.safetyIndex?.roundToInt(), it.rank) },
        )
    }
}
