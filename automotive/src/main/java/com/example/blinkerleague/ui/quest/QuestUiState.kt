package com.example.blinkerleague.ui.quest

import com.example.blinkerleague.domain.model.TodayMission

/** 퀘스트 상세 (SCR-03, screen-plan 4.6) */
sealed interface QuestUiState {
    data object Loading : QuestUiState

    data class Loaded(
        val mission: TodayMission,
        /** 이 퀘스트와 연승의 관계. 연승을 못 받으면 null (그 줄만 숨김) */
        val streakImpact: StreakImpact? = null,
        /** 클리어 축하(꽃가루)를 보여 줄지. 같은 회차는 한 번만 (앱을 다시 켜도) */
        val celebrate: Boolean = false,
    ) : QuestUiState

    data object Unavailable : QuestUiState
}

/**
 * 퀘스트 클리어가 연승에 주는 영향 (SCR-03 "연승 영향")
 * - [todayCleared] false: "클리어하면 [days]연승"
 * - [todayCleared] true: "오늘 클리어로 [days]연승"
 */
data class StreakImpact(
    val days: Int,
    val todayCleared: Boolean,
    /** 그 연승이 연속 보상 주기(기준값 point.streakRewardDays)에 닿으면 보상 포인트, 아니면 null */
    val rewardPoint: Int? = null,
)
