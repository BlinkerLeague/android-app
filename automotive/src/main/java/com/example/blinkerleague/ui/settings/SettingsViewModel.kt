package com.example.blinkerleague.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.data.auth.DriverProfile
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.domain.region.Regions
import com.example.blinkerleague.service.RecordingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    profile: () -> DriverProfile?,
    speedPermission: Flow<PermissionStatus>,
    recordingStatus: Flow<RecordingStatus>,
    version: String,
    usesServer: Boolean,
    /** 지금 순위 공개 설정 (리그 조회 `me.visible`). 받지 못하면 null */
    private val loadLeagueVisible: suspend () -> Boolean? = { null },
    /** 순위 공개 설정 저장. 서버에 저장되면 true */
    private val saveLeagueVisible: suspend (Boolean) -> Boolean = { false },
) : ViewModel() {

    private val me = profile()

    /** 순위 공개 토글 상태: 값(null이면 숨김)과 저장 실패 여부 */
    private val league = MutableStateFlow(LeagueToggle(visible = null, failed = false))

    init {
        viewModelScope.launch { league.value = LeagueToggle(loadLeagueVisible(), failed = false) }
    }

    val uiState: StateFlow<SettingsUiState> = combine(speedPermission, recordingStatus, league) { speed, status, toggle ->
        SettingsUiState(
            nickname = me?.nickname,
            region = me?.regionCode?.let { Regions.label(it) ?: it },
            speedPermission = speed,
            recordingStatus = status,
            version = version,
            usesServer = usesServer,
            leagueVisible = toggle.visible,
            leagueVisibilityFailed = toggle.failed,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(nickname = me?.nickname, region = me?.regionCode?.let { Regions.label(it) ?: it }, version = version, usesServer = usesServer),
    )

    /** "리그 순위 공개" 토글 */
    fun setLeagueVisible(visible: Boolean) {
        // 저장 중에는 다시 요청하지 않음 (연속 토글 경합 방지)
        if (league.value.saving) return
        league.value = league.value.copy(saving = true)
        viewModelScope.launch {
            var saved = false
            try {
                saved = saveLeagueVisible(visible)
            } finally {
                league.value = if (saved) LeagueToggle(visible, failed = false) else league.value.copy(failed = true, saving = false)
            }
        }
    }

    private data class LeagueToggle(val visible: Boolean?, val failed: Boolean, val saving: Boolean = false)
}
