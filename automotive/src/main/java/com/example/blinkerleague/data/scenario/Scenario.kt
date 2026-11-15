package com.example.blinkerleague.data.scenario

import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.HeadlightSample
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.VehicleStateSample

/**
 * 실차 없이 재생하는 위치·신호 시계열. 타임스탬프는 시나리오 시작 기준(0부터)의 ms.
 */
data class Scenario(
    val name: String,
    val locations: List<LocationSample>,
    val steering: List<SteeringSample>,
    val signals: List<SignalSample>,
    val vehicleStates: List<VehicleStateSample>,
    val supported: Set<SignalType> = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE),
    /** 브레이크 페달 상태 변화 */
    val brakes: List<BrakeSample> = emptyList(),
    /** 하향등 상태 변화 */
    val headlights: List<HeadlightSample> = emptyList(),
) {
    val durationMs: Long
        get() = listOfNotNull(
            locations.lastOrNull()?.timestampMs,
            steering.lastOrNull()?.timestampMs,
            signals.lastOrNull()?.timestampMs,
            vehicleStates.lastOrNull()?.timestampMs,
        ).maxOrNull() ?: 0L
}
