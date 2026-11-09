package com.example.blinkerleague.data.remote.dto

import kotlinx.serialization.Serializable

// 2차(P1) API 형식. 서버 Swagger(/v3/api-docs) 기준: GET /api/points, /api/streak, /api/badges, /api/reports/weekly

@Serializable
data class PointsResponse(
    val balance: Int,
    val todayEarned: Int = 0,
    val dailyCap: Int? = null,
    val entries: List<PointEntryDto> = emptyList(),
)

@Serializable
data class PointEntryDto(
    val amount: Int,
    /** `TRIP`, `DAILY_MISSION`, `STREAK_7` (서버가 늘릴 수 있음) */
    val reason: String,
    /** `TRIP` 적립 시점의 거리·평점. 다른 사유는 null */
    val description: PointDescriptionDto? = null,
    val createdAt: String,
)

@Serializable
data class PointDescriptionDto(val distanceKm: Double? = null, val score: Int? = null)

@Serializable
data class StreakResponse(val streakDays: Int, val todayAchieved: Boolean = false)

/** 배지 진열장 항목. 획득하지 않은 배지도 잠금 상태로 옴 */
@Serializable
data class BadgeDto(
    val code: String,
    val name: String,
    val condition: String,
    val earned: Boolean,
    val awardedAt: String? = null,
)

@Serializable
data class WeeklyReportResponse(
    /** 예: 2026-W45 */
    val week: String,
    val trips: Int = 0,
    val distanceKm: Double = 0.0,
    val metrics: List<MetricChangeDto> = emptyList(),
    val weakestMetric: String? = null,
    val lastMission: WeeklyMissionDto? = null,
    val nextMission: WeeklyMissionDto? = null,
)

@Serializable
data class MetricChangeDto(
    val metric: String,
    val thisWeek: Int? = null,
    val lastWeek: Int? = null,
    val change: Int? = null,
)

/** 맞춤 주간 미션 (서버 D7: 리포트를 만들 때 다음 주로 자동 등록, 수락 API 없음) */
@Serializable
data class WeeklyMissionDto(
    val week: String,
    val metric: String,
    val title: String,
    val target: Int? = null,
    val minDistanceKm: Double? = null,
    /** `ASSIGNED`, `ACHIEVED`, `FAILED` */
    val status: String,
)
