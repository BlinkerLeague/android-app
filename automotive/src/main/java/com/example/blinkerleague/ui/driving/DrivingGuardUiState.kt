package com.example.blinkerleague.ui.driving

import com.example.blinkerleague.domain.detection.GuardState
import com.example.blinkerleague.service.RecordingStatus

data class DrivingGuardUiState(
    /** null이면 아직 기어 값을 기다리는 중 (앱 시작 직후) */
    val guard: GuardState? = null,
    val recordingStatus: RecordingStatus = RecordingStatus.OFF,
) {
    /** 정차 화면(NavHost)을 보여도 되는지. 판정 전에는 열지 않음 */
    val showParkedScreens: Boolean get() = guard == GuardState.PARKED
}
