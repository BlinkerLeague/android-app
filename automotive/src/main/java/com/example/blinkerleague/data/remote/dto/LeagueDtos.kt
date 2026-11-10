package com.example.blinkerleague.data.remote.dto

import kotlinx.serialization.Serializable

// GET /api/leagues/current — 서버 4.4 지역 리그(서버 #48 · #49). 형식은 docs/api/openapi.json `CurrentLeagueResponse`.
// 서버 요청 14번 제안 형식(myLeague)은 서버가 아래 형식으로 구현해 대체됨. 모든 값은 선택값

@Serializable
data class LeagueResponse(
    val season: LeagueSeasonDto,
    /** 내 리그(소속 권역). 등록 지역이 권역 매핑표에 없으면 null */
    val league: LeagueDto? = null,
    /** 내 시즌 기록 */
    val me: LeagueMeDto? = null,
    /** 권역별 지역 안전지수 (SCR-04 월드맵). 리그 점수가 없는 권역은 safetyIndex · rank가 null */
    val regions: List<LeagueRegionDto> = emptyList(),
)

/** 달력 월 시즌 (서버 D29). seasonId 예: 2026-11 */
@Serializable
data class LeagueSeasonDto(val seasonId: String, val startsAt: String, val endsAt: String)

@Serializable
data class LeagueDto(
    /** 리그 권역 코드 (서버 D31 매핑표, 예: SEOUL_NORTHWEST) */
    val leagueCode: String,
    val name: String? = null,
    /** 참여자 수. 10명 미만이면 순위 비공개(서버 D11) */
    val participants: Int = 0,
    val rankingPublished: Boolean = false,
    val safetyIndex: Double? = null,
    val rank: Int? = null,
    /** 상위 순위(닉네임만). 비공개면 빈 목록 */
    val rankings: List<LeagueRankingDto> = emptyList(),
)

/** 리그 순위 점수 score = 평균 점수 averageScore + 이번 시즌 포인트 points (서버 D34) */
@Serializable
data class LeagueRankingDto(
    val rank: Int,
    val nickname: String,
    val averageScore: Double? = null,
    val points: Int = 0,
    val score: Double = 0.0,
)

@Serializable
data class LeagueMeDto(
    /** 순위 공개 설정 (서버 D8, 기본 공개) */
    val visible: Boolean = true,
    /** 순위 자격(서버 D13: 시즌 누적 50km 또는 10회) */
    val eligible: Boolean = false,
    val averageScore: Double? = null,
    val points: Int = 0,
    val score: Double? = null,
    /** 순위 공개를 껐거나 자격이 없으면 null */
    val rank: Int? = null,
    val trips: Int = 0,
    val distanceKm: Double = 0.0,
    /** 지난 시즌 우승 칭호 (서버 D30 · D34) */
    val championships: List<LeagueChampionshipDto> = emptyList(),
)

@Serializable
data class LeagueChampionshipDto(val seasonId: String, val leagueCode: String, val name: String? = null)

@Serializable
data class LeagueRegionDto(
    val leagueCode: String,
    val name: String? = null,
    /** 지역 안전지수 0~100 (서버 D5). 리그 점수가 없으면 null */
    val safetyIndex: Double? = null,
    val rank: Int? = null,
)
