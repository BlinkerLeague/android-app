package com.example.blinkerleague.ui.driving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.domain.config.GuardConfig
import com.example.blinkerleague.domain.detection.DrivingGuard
import com.example.blinkerleague.domain.detection.GuardState
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.service.RecordingStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * 앱 최상위에서 주행 중인지 판단함. 주행 중이면 MainActivity가 정차 화면 대신 기록 중 화면을 그림.
 *
 * @param gears 기어 변화 (실제: Vehicle SDK Gear)
 * @param speeds 속도 km/h (실제: CarProperty, 권한 없으면 오지 않음 → 측정 안 됨으로 봄)
 * @param bypass 디버그 빌드 시연용 우회 (릴리스에서는 항상 false)
 */
class DrivingGuardViewModel(
    gears: Flow<Gear>,
    speeds: Flow<Float>,
    recordingStatus: Flow<RecordingStatus>,
    bypass: Flow<Boolean>,
    private val config: GuardConfig,
) : ViewModel() {

    /** 기어를 받기 전 null. 시간 안에 못 받으면 UNKNOWN */
    private val gear: Flow<Gear?> = combine(
        gears.onStart<Gear?> { emit(null) },
        flow {
            emit(false)
            delay(config.gearTimeoutMs)
            emit(true)
        },
    ) { gear, timedOut -> gear ?: if (timedOut) Gear.UNKNOWN else null }

    val uiState: StateFlow<DrivingGuardUiState> = combine(
        gear,
        speeds.onStart<Float?> { emit(null) },
        recordingStatus,
        bypass,
    ) { gear, speed, status, bypassed ->
        val guard = when {
            bypassed -> GuardState.PARKED
            gear == null -> null
            else -> DrivingGuard.evaluate(gear, speed, config)
        }
        DrivingGuardUiState(guard, status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrivingGuardUiState())
}
