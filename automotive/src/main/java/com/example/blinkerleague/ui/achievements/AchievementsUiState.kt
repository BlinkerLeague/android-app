package com.example.blinkerleague.ui.achievements

import com.example.blinkerleague.domain.config.PointConfig

/** 업적 (SCR-05): 연속 달성 + 배지 진열장 */
sealed interface AchievementsUiState {
    data object Loading : AchievementsUiState

    data object Unavailable : AchievementsUiState

    data class Loaded(
        /** 연속 달성. 못 받으면 null (그 칸만 숨김) */
        val streak: StreakCard?,
        val badges: List<BadgeCard>,
    ) : AchievementsUiState
}

data class StreakCard(
    val days: Int,
    val todayAchieved: Boolean,
    /** 다음 연속 보상까지 남은 날 (기준값 point.streakRewardDays마다 streakRewardPoint) */
    val daysToReward: Int,
    /** 연속 보상 포인트 (서버 기준값 point.streakRewardPoint) */
    val rewardPoint: Int = PointConfig().streakRewardPoint,
)

data class BadgeCard(
    val name: String,
    val condition: String,
    val earned: Boolean,
    /** 예: "11.09". 못 받았거나 서버가 안 주면 null */
    val awardedOn: String?,
)

/**
 * 다음 보상까지 남은 날: 0일·7일째면 7, 3일째면 4
 * period는 1 이상(ConfigRepository.mergedWith에서 보장)
 */
internal fun daysToReward(days: Int, period: Int = PointConfig().streakRewardDays): Int = period - days % period
