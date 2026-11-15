package com.example.blinkerleague.data.vehicle

import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.CapabilityReport
import com.example.blinkerleague.domain.model.HeadlightSample
import com.example.blinkerleague.domain.model.SeatBeltSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.TelltaleSample
import com.example.blinkerleague.domain.model.VehicleStateSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 차량 신호 공급원. 실제 구현체([PleosVehicleSignalSource])와 Fake 구현체([FakeVehicleSignalSource])가 있음.
 * domain/ViewModel은 이 인터페이스만 알고 Vehicle SDK·CarProperty 타입은 모름.
 *
 * 각 Flow는 수집을 시작하면 현재 값을 먼저 한 번 보내고(SDK 리스너가 등록 직후 현재 값을 보냄), 이후 바뀔 때마다 보냄.
 * 지원하지 않거나 권한이 없는 신호의 Flow는 아무것도 보내지 않고 끝남(예외를 던지지 않음).
 */
interface VehicleSignalSource {
    /** 이 차량에서 쓸 수 있는 신호 (SDK checkXXXCapability·권한 확인 결과) */
    suspend fun capabilities(): CapabilityReport

    /** 업로드 `supported` 목록. 기본은 [capabilities]에서 계산 */
    suspend fun supportedSignals(): Set<SignalType> = capabilities().supportedSignals

    /** 핸들 각도 변화 (회전 감지 입력) */
    val steeringAngles: Flow<SteeringSample>

    /** 방향지시등 상태 변화 */
    val turnSignals: Flow<SignalSample>

    /** 기어(와 알 수 있으면 시동) 상태 변화 */
    val vehicleStates: Flow<VehicleStateSample>

    /** 차량 속도 (km/h). 정차 판단, 저속 회전 제외, 운행 거리(적분)에 씀 */
    val speeds: Flow<SpeedSample>

    /** 브레이크 페달 눌림 상태 변화 (급감속 판정 입력). 기본은 보내지 않음(미지원) */
    val brakes: Flow<BrakeSample>
        get() = emptyFlow()

    /** 하향등 켜짐 상태 변화 (야간 전조등 판정 입력). 기본은 보내지 않음(미지원) */
    val headlights: Flow<HeadlightSample>
        get() = emptyFlow()

    /** 운전석 안전벨트 착용 상태 (안전벨트 판정 입력). 출발 시와 주행 중 주기적으로 읽은 값. 기본은 보내지 않음(미지원) */
    val seatBelts: Flow<SeatBeltSample>
        get() = emptyFlow()

    /** 그 시점에 켜진 경고등 이름 집합 (안전 경고등 판정 입력, 1초 폴링). 기본은 보내지 않음(미지원) */
    val telltales: Flow<TelltaleSample>
        get() = emptyFlow()
}
