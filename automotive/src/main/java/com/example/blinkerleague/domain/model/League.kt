package com.example.blinkerleague.domain.model

/** 지역 리그 (SCR-04) */
data class League(
    val seasonId: String,
    val seasonEndsAtMs: Long,
    val myLeague: MyLeague?,
    val regions: List<LeagueRegion>,
    /** 지난 시즌 우승 칭호 (서버 D30 · D34) */
    val championships: List<Championship> = emptyList(),
)

/** 우승 칭호: 시즌(예: 2026-10)과 권역 이름 */
data class Championship(val seasonId: String, val name: String)

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
    /** 리그 점수 = 평균 점수 + 이번 시즌 포인트 (서버 D34) */
    val averageScore: Int? = null,
    val points: Int = 0,
)

data class LeagueEntry(val rank: Int, val nickname: String, val score: Int, val me: Boolean)

data class LeagueRegion(val name: String, val safetyIndex: Int?, val rank: Int?)
