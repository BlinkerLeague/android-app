package com.example.blinkerleague.data.remote.dto

import kotlinx.serialization.Serializable

// GET /api/config/thresholds 형식. 서버 ThresholdController.ThresholdsResponse 기준 (서버 plan.md 3.3).
// 앱이 쓰는 항목만 두고 나머지(harsh, weights, point의 dailyCap · tripMaxKm 등)는 무시함. 서버가 아직 안 주는 필드도 있어 모두 선택값

@Serializable
data class ThresholdsResponse(
    val version: Long,
    val trip: TripThresholdsDto? = null,
    val turn: TurnThresholdsDto? = null,
    val grades: List<GradeRuleDto>? = null,
    /** 항목 이름 → 팁 문구 */
    val tips: Map<String, String>? = null,
    val league: LeagueThresholdsDto? = null,
    val point: PointThresholdsDto? = null,
)

@Serializable
data class TripThresholdsDto(
    val minDistanceKm: Double? = null,
    val parkEndMinutes: Int? = null,
)

/** 핸들 각도 항목(steering*, mergeGapSec)은 서버 요청 1·7번. 서버가 주기 전까지는 앱 기본값 */
@Serializable
data class TurnThresholdsDto(
    val steeringEnterDeg: Float? = null,
    val steeringExitDeg: Float? = null,
    val steeringRightPositive: Boolean? = null,
    val minSpeedKmh: Float? = null,
    val signalLeadSec: Int? = null,
    val mergeGapSec: Int? = null,
    val windowSec: Int? = null,
    val minAngleDeg: Float? = null,
    val uTurnAngleDeg: Float? = null,
)

@Serializable
data class GradeRuleDto(val grade: String, val minScore: Int)

/** 지역 리그 기준 (서버 D11 · D13). commonItems · topN은 서버 계산용이라 앱은 읽지 않음 */
@Serializable
data class LeagueThresholdsDto(
    val minParticipants: Int? = null,
    val minSeasonDistanceKm: Double? = null,
    val minSeasonTrips: Int? = null,
)

/** 포인트 기준 (openapi `Point`). dailyCap · tripMaxKm는 서버 계산용이라 앱은 읽지 않음 */
@Serializable
data class PointThresholdsDto(
    val streakRewardDays: Int? = null,
    val streakRewardPoint: Int? = null,
)
