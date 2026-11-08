package com.example.blinkerleague.ui.quest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.domain.model.TodayMission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QuestViewModel(private val loadMission: suspend () -> TodayMission?) : ViewModel() {

    private val _uiState = MutableStateFlow<QuestUiState>(QuestUiState.Loading)
    val uiState: StateFlow<QuestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = loadMission()?.let { QuestUiState.Loaded(it) } ?: QuestUiState.Unavailable
        }
    }
}
