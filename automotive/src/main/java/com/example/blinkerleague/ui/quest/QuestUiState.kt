package com.example.blinkerleague.ui.quest

import com.example.blinkerleague.domain.model.TodayMission

/** 퀘스트 상세 (SCR-03, screen-plan 4.6) */
sealed interface QuestUiState {
    data object Loading : QuestUiState

    data class Loaded(val mission: TodayMission) : QuestUiState

    data object Unavailable : QuestUiState
}
