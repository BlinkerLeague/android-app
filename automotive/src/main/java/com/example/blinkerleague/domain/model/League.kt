package com.example.blinkerleague.domain.model

/** 지역 리그 (SCR-04) */
data class League(
    val seasonId: String,
    val seasonEndsAtMs: Long,
    val myLeague: MyLeague?,
    val regions: List<LeagueRegion>,
)

data class MyLeague(
    val name: String,
    val participants: Int,
    val rankPublic: Boolean,
    val myRank: Int?,
    val myScore: Int?,
    val qualified: Boolean,
    val seasonKm: Double,
    val seasonTrips: Int,
    val top: List<LeagueEntry>,
)

data class LeagueEntry(val rank: Int, val nickname: String, val score: Int, val me: Boolean)

data class LeagueRegion(val name: String, val safetyIndex: Int?, val rank: Int?)
