package com.example.blinkerleague.ui.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.data.repository.MissionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MissionsViewModel(private val repository: MissionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<MissionsUiState>(MissionsUiState.Loading)
    val uiState: StateFlow<MissionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = repository.todayMission()?.let { MissionsUiState.Loaded(it) } ?: MissionsUiState.Unavailable
        }
    }
}
