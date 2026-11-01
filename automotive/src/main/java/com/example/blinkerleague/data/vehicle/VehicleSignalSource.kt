package com.example.blinkerleague.data.vehicle

import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.VehicleStateSample
import kotlinx.coroutines.flow.Flow

/**
 * 차량 신호 공급원. SDK 구현체와 Fake 구현체([FakeVehicleSignalSource])가 있음.
 * domain/ViewModel은 이 인터페이스만 알고 Vehicle SDK 타입은 모름.
 */
interface VehicleSignalSource {
    /** 이 차량에서 측정 가능한 신호 (SDK checkXXXCapability 결과). 없는 신호는 측정에서 제외함 */
    suspend fun supportedSignals(): Set<SignalType>

    /** 핸들 각도 변화 (회전 감지 입력) */
    val steeringAngles: Flow<SteeringSample>

    /** 방향지시등 상태 변화 */
    val turnSignals: Flow<SignalSample>

    /** 시동·기어 상태 변화 */
    val vehicleStates: Flow<VehicleStateSample>
}
