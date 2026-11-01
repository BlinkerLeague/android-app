package com.example.blinkerleague.ui.summary

import com.example.blinkerleague.domain.model.ScoreState
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
    ) : SummaryUiState

    data object NotFound : SummaryUiState
}

data class TurnRow(
    /** 예: "08:02" */
    val time: String,
    val direction: TurnDirection,
    val signalOn: Boolean,
)
