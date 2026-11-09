package com.example.blinkerleague.data.remote.dto

import kotlinx.serialization.Serializable

// GET /api/leagues/current — 서버 4.4(지역 리그) 미구현. 서버 plan.md 4.4 응답 설명을 바탕으로 앱이 제안한 형식
// (docs/server-requests.md 14번). 서버가 다르게 정하면 여기만 맞춤. 모든 값은 선택값

@Serializable
data class LeagueResponse(
    val season: LeagueSeasonDto,
    /** 내 리그(소속 권역). 등록 지역이 권역에 매핑되지 않으면 null */
    val myLeague: MyLeagueDto? = null,
    /** 시즌 내 권역별 지역 안전지수 (SCR-04 월드맵) */
    val regions: List<LeagueRegionDto> = emptyList(),
)

/** 달력 월 시즌 (서버 D29). seasonId 예: 2026-11 */
@Serializable
data class LeagueSeasonDto(val seasonId: String, val startsAt: String, val endsAt: String)

@Serializable
data class MyLeagueDto(
    /** 리그 권역 코드 (서버 D31 매핑표) */
    val region: String,
    /** 권역 이름 (예: 서북권, 부산) */
    val name: String? = null,
    /** 순위 자격자 수. 10명 미만이면 순위 비공개(서버 D11) */
    val participants: Int = 0,
    val rankPublic: Boolean = false,
    val myRank: Int? = null,
    /** 내 시즌 리그 점수 (공통 항목, 서버 D19) */
    val myScore: Int? = null,
    /** 순위 자격(서버 D13: 시즌 누적 50km 또는 10회) */
    val qualified: Boolean = false,
    val seasonKm: Double = 0.0,
    val seasonTrips: Int = 0,
    val top: List<LeagueEntryDto> = emptyList(),
)

@Serializable
data class LeagueEntryDto(val rank: Int, val nickname: String, val score: Int, val me: Boolean = false)

@Serializable
data class LeagueRegionDto(
    val region: String,
    val name: String? = null,
    /** 지역 안전지수 0~100 (서버 D5). 순위 비공개 권역은 null */
    val safetyIndex: Int? = null,
    val rank: Int? = null,
)
