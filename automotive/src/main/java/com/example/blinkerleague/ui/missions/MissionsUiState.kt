package com.example.blinkerleague.ui.missions

import com.example.blinkerleague.domain.model.TodayMission

sealed interface MissionsUiState {
    data object Loading : MissionsUiState
    data class Loaded(val mission: TodayMission) : MissionsUiState
    data object Unavailable : MissionsUiState
}
