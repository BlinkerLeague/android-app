package com.example.blinkerleague.domain.model

/** 포인트 지갑 (SCR-06). 서버 원장 기준 */
data class PointWallet(
    val balance: Int,
    val todayEarned: Int,
    /** 하루 적립 상한. 서버가 안 주면 null */
    val dailyCap: Int?,
    val entries: List<PointEntry>,
)

enum class PointReason { TRIP, DAILY_MISSION, STREAK_7, OTHER }

data class PointEntry(
    val amount: Int,
    val reason: PointReason,
    /** 운행 적립일 때 적립 시점의 거리·평점 (거리 × 평점) */
    val distanceKm: Double?,
    val score: Int?,
    val createdAtMs: Long,
)

/** 미션 연속 달성 (오늘 달성했으면 오늘부터, 아니면 어제부터 센 일수) */
data class Streak(val days: Int, val todayAchieved: Boolean)

/** 배지 진열장 항목 (SCR-05). 잠긴 배지도 조건 문구와 함께 옴 */
data class Badge(
    val code: String,
    val name: String,
    val condition: String,
    val earned: Boolean,
    val awardedAtMs: Long?,
)

/** 주간 리포트 (SCR-07) */
data class WeeklyReport(
    /** 예: 2026-W45 */
    val week: String,
    val trips: Int,
    val distanceKm: Double,
    val metrics: List<MetricChange>,
    /** 가장 약한 항목 (서버 항목 이름, 예: TURN_SIGNAL) */
    val weakestMetric: String?,
    val lastMission: WeeklyMission?,
    val nextMission: WeeklyMission?,
)

data class MetricChange(val metric: String, val thisWeek: Int?, val lastWeek: Int?, val change: Int?)

enum class WeeklyMissionStatus { ASSIGNED, ACHIEVED, FAILED }

data class WeeklyMission(
    val week: String,
    val metric: String,
    val title: String,
    val target: Int?,
    val minDistanceKm: Double?,
    val status: WeeklyMissionStatus,
)
