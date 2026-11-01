package com.example.blinkerleague.ui.summary

import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TurnDirection

sealed interface SummaryUiState {
    data object Loading : SummaryUiState

    data class Loaded(
        val scoreState: ScoreState,
        val score: Int?,
        val distanceKm: Double,
        val successTurns: Int,
        val totalTurns: Int,
        val turns: List<TurnRow>,
        /** 업로드 후 받아온 오늘의 미션. 못 받으면 null (카드 숨김) */
        val mission: TodayMission? = null,
    ) : SummaryUiState

    data object NotFound : SummaryUiState
}

data class TurnRow(
    /** 예: "08:02" */
    val time: String,
    val direction: TurnDirection,
    val signalOn: Boolean,
)
