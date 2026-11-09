package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.LeagueApi
import com.example.blinkerleague.domain.model.League
import com.example.blinkerleague.domain.model.LeagueEntry
import com.example.blinkerleague.domain.model.LeagueRegion
import com.example.blinkerleague.domain.model.MyLeague
import java.time.OffsetDateTime

/** 지역 리그. 받지 못하면 null (서버 리그 API가 아직 없으면 404 → null) */
class LeagueRepository(private val api: LeagueApi) {
    suspend fun current(): League? {
        val body = (api.currentLeague() as? ApiResult.Success)?.body ?: return null
        val endsAt = runCatching { OffsetDateTime.parse(body.season.endsAt).toInstant().toEpochMilli() }.getOrNull() ?: return null
        return League(
            seasonId = body.season.seasonId,
            seasonEndsAtMs = endsAt,
            myLeague = body.myLeague?.let { m ->
                MyLeague(
                    name = m.name ?: m.region,
                    participants = m.participants,
                    rankPublic = m.rankPublic,
                    myRank = m.myRank,
                    myScore = m.myScore,
                    qualified = m.qualified,
                    seasonKm = m.seasonKm,
                    seasonTrips = m.seasonTrips,
                    top = m.top.map { LeagueEntry(it.rank, it.nickname, it.score, it.me) },
                )
            },
            regions = body.regions.map { LeagueRegion(it.name ?: it.region, it.safetyIndex, it.rank) },
        )
    }
}
