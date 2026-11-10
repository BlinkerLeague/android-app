package com.example.blinkerleague.ui.settings

import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.service.RecordingStatus

/** 설정 (SCR-08) */
data class SettingsUiState(
    val nickname: String? = null,
    /** 예: "서울 마포구". 목록에 없는 코드면 코드 그대로 */
    val region: String? = null,
    val speedPermission: PermissionStatus = PermissionStatus.NOT_REQUESTED,
    val recordingStatus: RecordingStatus = RecordingStatus.OFF,
    val version: String = "",
    /** 서버에 연결된 빌드인지 (아니면 체험 모드: 기기 안에서만 점수 계산) */
    val usesServer: Boolean = false,
    /** 리그 순위 공개 (서버 D8). 아직 모르거나 받지 못하면 null → 토글을 숨김 */
    val leagueVisible: Boolean? = null,
    /** 순위 공개 설정을 서버에 저장하지 못함 */
    val leagueVisibilityFailed: Boolean = false,
    /** 순위 공개 설정을 서버에 저장하는 중 (토글을 막음) */
    val leagueVisibilitySaving: Boolean = false,
)
