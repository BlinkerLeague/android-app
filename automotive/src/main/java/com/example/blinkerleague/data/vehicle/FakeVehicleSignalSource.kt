package com.example.blinkerleague.data.vehicle

import com.example.blinkerleague.data.scenario.Scenario
import com.example.blinkerleague.data.scenario.replay
import com.example.blinkerleague.domain.model.Availability
import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.CapabilityReport
import com.example.blinkerleague.domain.model.HeadlightSample
import com.example.blinkerleague.domain.model.SeatBeltSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.VehicleStateSample
import kotlinx.coroutines.flow.Flow

/**
 * 시나리오를 재생하는 차량 신호 공급원. [FakeLocationSource]와 같은 [startMs]·[speed]로 만들면 시계열이 맞음.
 * 속도는 시나리오 위치 샘플의 속도를 그대로 씀(1초 간격).
 *
 * @param startMs 시나리오 0ms에 해당하는 시각. 내보내는 타임스탬프에 더해짐
 */
class FakeVehicleSignalSource(
    private val scenario: Scenario,
    private val startMs: Long = 0L,
    private val speed: Float = 1f,
) : VehicleSignalSource {

    override suspend fun capabilities(): CapabilityReport {
        fun of(type: SignalType) = if (type in scenario.supported) Availability.SUPPORTED else Availability.UNSUPPORTED
        return CapabilityReport(
            turnSignal = of(SignalType.TURN_SIGNAL),
            steering = of(SignalType.STEERING_ANGLE),
            gear = Availability.SUPPORTED,
            speed = Availability.SUPPORTED,
            brake = of(SignalType.BRAKE),
            headlight = of(SignalType.HEADLIGHT),
            seatBelt = of(SignalType.SEAT_BELT),
        )
    }

    override val steeringAngles: Flow<SteeringSample> =
        replay(scenario.steering, { it.timestampMs }, speed) { it.copy(timestampMs = startMs + it.timestampMs) }

    override val turnSignals: Flow<SignalSample> =
        replay(scenario.signals, { it.timestampMs }, speed) { it.copy(timestampMs = startMs + it.timestampMs) }

    override val vehicleStates: Flow<VehicleStateSample> =
        replay(scenario.vehicleStates, { it.timestampMs }, speed) { it.copy(timestampMs = startMs + it.timestampMs) }

    override val speeds: Flow<SpeedSample> = replay(
        scenario.locations.mapNotNull { l -> l.speedKmh?.let { SpeedSample(l.timestampMs, it) } },
        { it.timestampMs },
        speed,
    ) { it.copy(timestampMs = startMs + it.timestampMs) }

    override val brakes: Flow<BrakeSample> =
        replay(scenario.brakes, { it.timestampMs }, speed) { it.copy(timestampMs = startMs + it.timestampMs) }

    override val headlights: Flow<HeadlightSample> =
        replay(scenario.headlights, { it.timestampMs }, speed) { it.copy(timestampMs = startMs + it.timestampMs) }

    override val seatBelts: Flow<SeatBeltSample> =
        replay(scenario.seatBelts, { it.timestampMs }, speed) { it.copy(timestampMs = startMs + it.timestampMs) }
}
