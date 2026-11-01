package com.example.blinkerleague.data.location

import com.example.blinkerleague.data.scenario.Scenario
import com.example.blinkerleague.data.scenario.replay
import com.example.blinkerleague.domain.model.LocationSample
import kotlinx.coroutines.flow.Flow

/** 시나리오를 재생하는 위치 공급원. 인자는 [com.example.blinkerleague.data.vehicle.FakeVehicleSignalSource]와 같음. */
class FakeLocationSource(
    private val scenario: Scenario,
    private val startMs: Long = 0L,
    private val speed: Float = 1f,
) : LocationSource {

    override val locations: Flow<LocationSample> =
        replay(scenario.locations, { it.timestampMs }, speed) { it.copy(timestampMs = startMs + it.timestampMs) }
}
