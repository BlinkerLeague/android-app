package com.example.blinkerleague.data.vehicle

import com.example.blinkerleague.data.scenario.Scenario
import com.example.blinkerleague.data.scenario.replay
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.VehicleStateSample
import kotlinx.coroutines.flow.Flow

/**
 * 시나리오를 재생하는 차량 신호 공급원. [FakeLocationSource]와 같은 [startMs]·[speed]로 만들면 시계열이 맞음.
 *
 * @param startMs 시나리오 0ms에 해당하는 시각. 내보내는 타임스탬프에 더해짐
 */
class FakeVehicleSignalSource(
    private val scenario: Scenario,
    private val startMs: Long = 0L,
    private val speed: Float = 1f,
) : VehicleSignalSource {

    override suspend fun supportedSignals(): Set<SignalType> = scenario.supported

    override val turnSignals: Flow<SignalSample> =
        replay(scenario.signals, { it.timestampMs }, speed) { it.copy(timestampMs = startMs + it.timestampMs) }

    override val vehicleStates: Flow<VehicleStateSample> =
        replay(scenario.vehicleStates, { it.timestampMs }, speed) { it.copy(timestampMs = startMs + it.timestampMs) }
}
