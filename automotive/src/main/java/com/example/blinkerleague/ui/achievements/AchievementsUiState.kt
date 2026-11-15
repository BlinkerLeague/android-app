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
        /** 획득한 배지 수 ("획득 N / 전체 M"의 N) */
        val earnedCount: Int = 0,
    ) : AchievementsUiState
}

data class StreakCard(
    val days: Int,
    val todayAchieved: Boolean,
    /** 다음 연속 보상까지 남은 날 (기준값 point.streakRewardDays마다 streakRewardPoint) */
    val daysToReward: Int,
    /** 연속 보상 포인트 (서버 기준값 point.streakRewardPoint) */
    val rewardPoint: Int = PointConfig().streakRewardPoint,
    /** 연속 보상 주기 = 진행 점 칸 수 (서버 기준값 point.streakRewardDays) */
    val period: Int = PointConfig().streakRewardDays,
)

data class BadgeCard(
    val name: String,
    val condition: String,
    val earned: Boolean,
    /** 예: "11.09". 못 받았거나 서버가 안 주면 null */
    val awardedOn: String?,
    /** 서버 배지 코드 (예: FIRST_TRIP). 진열장 아이콘을 고를 때만 씀 */
    val code: String = "",
)

/**
 * 다음 보상까지 남은 날: 0일·7일째면 7, 3일째면 4
 * period는 1 이상(ConfigRepository.mergedWith에서 보장)
 */
internal fun daysToReward(days: Int, period: Int = PointConfig().streakRewardDays): Int = period - days % period

/**
 * 연승 진행 점 중 채울 칸 수 (한 주기 [period]칸): 0일이면 0, 3일이면 3, 7일 · 14일째면 7(보상 받는 날은 꽉 참)
 * period는 1 이상(ConfigRepository.mergedWith에서 보장)
 */
internal fun streakDotsFilled(days: Int, period: Int = PointConfig().streakRewardDays): Int =
    if (days <= 0) 0 else (days - 1) % period + 1
