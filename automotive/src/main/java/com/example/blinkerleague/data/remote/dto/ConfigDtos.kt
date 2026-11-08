package com.example.blinkerleague.data.remote.dto

import kotlinx.serialization.Serializable

// GET /api/config/thresholds 형식. 서버 ThresholdController.ThresholdsResponse 기준 (서버 plan.md 3.3).
// 앱이 쓰는 항목만 두고 나머지(harsh, weights, point, tips 등)는 무시함. 서버가 아직 안 주는 필드도 있어 모두 선택값

@Serializable
data class ThresholdsResponse(
    val version: Long,
    val trip: TripThresholdsDto? = null,
    val turn: TurnThresholdsDto? = null,
    val grades: List<GradeRuleDto>? = null,
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
