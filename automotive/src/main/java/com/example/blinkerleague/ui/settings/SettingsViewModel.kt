package com.example.blinkerleague.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.data.auth.DriverProfile
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.domain.region.Regions
import com.example.blinkerleague.service.RecordingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    profile: () -> DriverProfile?,
    speedPermission: Flow<PermissionStatus>,
    recordingStatus: Flow<RecordingStatus>,
    version: String,
    usesServer: Boolean,
) : ViewModel() {

    private val me = profile()

    val uiState: StateFlow<SettingsUiState> = combine(speedPermission, recordingStatus) { speed, status ->
        SettingsUiState(
            nickname = me?.nickname,
            region = me?.regionCode?.let { Regions.label(it) ?: it },
            speedPermission = speed,
            recordingStatus = status,
            version = version,
            usesServer = usesServer,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(nickname = me?.nickname, region = me?.regionCode?.let { Regions.label(it) ?: it }, version = version, usesServer = usesServer),
    )
}
