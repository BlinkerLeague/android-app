package com.example.blinkerleague.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.data.repository.RegisterResult
import com.example.blinkerleague.domain.model.CapabilityReport
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.domain.region.Province
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 닉네임·지역 입력 → 측정 가능 항목 확인 → 속도 권한 요청 → 리그 입장(등록).
 * 속도 권한은 거부해도 입장할 수 있음 (기어만으로 판정, 홈에 "정확도 제한" 안내, screen-plan 9장)
 */
class OnboardingViewModel(
    provinces: List<Province>,
    speedPermission: Flow<PermissionStatus>,
    private val loadCapabilities: suspend () -> CapabilityReport,
    private val register: suspend (nickname: String, regionCode: String) -> RegisterResult,
) : ViewModel() {

    private val form = MutableStateFlow(OnboardingUiState(provinces = provinces))

    val uiState: StateFlow<OnboardingUiState> = combine(form, speedPermission) { form, speed ->
        form.copy(speedPermission = speed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    init {
        viewModelScope.launch {
            val report = loadCapabilities()
            form.update { it.copy(capabilities = report) }
        }
    }

    fun onNicknameChange(value: String) = form.update {
        it.copy(nickname = value.take(OnboardingUiState.NICKNAME_LENGTH.last), nicknameError = null, submitError = null)
    }

    fun onProvinceSelect(index: Int) = form.update { it.copy(provinceIndex = index, regionCode = null) }

    fun onRegionSelect(code: String) = form.update { it.copy(regionCode = code, submitError = null) }

    /** 권한 상태가 바뀌면 측정 가능 항목(속도)을 다시 확인 */
    fun refreshCapabilities() {
        viewModelScope.launch {
            val report = loadCapabilities()
            form.update { it.copy(capabilities = report) }
        }
    }

    fun submit() {
        val current = form.value
        val nickname = current.nickname.trim()
        if (nickname.length !in OnboardingUiState.NICKNAME_LENGTH) {
            form.update { it.copy(nicknameError = NicknameError.LENGTH) }
            return
        }
        val region = current.regionCode ?: return
        if (current.submitting) return
        form.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            val result = register(nickname, region)
            form.update {
                when (result) {
                    RegisterResult.Registered -> it.copy(submitting = false, registered = true)
                    RegisterResult.NicknameTaken -> it.copy(submitting = false, nicknameError = NicknameError.TAKEN)
                    RegisterResult.Invalid -> it.copy(submitting = false, nicknameError = NicknameError.LENGTH)
                    RegisterResult.Offline -> it.copy(submitting = false, submitError = SubmitError.OFFLINE)
                    RegisterResult.Failed -> it.copy(submitting = false, submitError = SubmitError.FAILED)
                }
            }
        }
    }
}
