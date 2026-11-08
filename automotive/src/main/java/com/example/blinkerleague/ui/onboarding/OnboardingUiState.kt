package com.example.blinkerleague.ui.onboarding

import com.example.blinkerleague.domain.model.CapabilityReport
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.domain.region.Province

/** 온보딩 (SCR-00, screen-plan 4.2) */
data class OnboardingUiState(
    val nickname: String = "",
    val nicknameError: NicknameError? = null,
    val provinces: List<Province> = emptyList(),
    val provinceIndex: Int = 0,
    val regionCode: String? = null,
    /** 내 차량 측정 가능 항목. 확인 중이면 null */
    val capabilities: CapabilityReport? = null,
    val speedPermission: PermissionStatus = PermissionStatus.NOT_REQUESTED,
    val submitting: Boolean = false,
    val submitError: SubmitError? = null,
    /** 등록 끝 → 홈으로 */
    val registered: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !submitting && regionCode != null && nickname.trim().length in NICKNAME_LENGTH

    companion object {
        val NICKNAME_LENGTH = 2..12
    }
}

enum class NicknameError { LENGTH, TAKEN }

enum class SubmitError { OFFLINE, FAILED }
