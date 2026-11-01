package com.example.blinkerleague.data.scenario

import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.TurnSignalState
import com.example.blinkerleague.domain.model.VehicleStateSample
import kotlin.math.cos
import kotlin.math.sin

/**
 * 시나리오를 동작 단위로 만드는 빌더. 위치는 1초마다 한 점씩, 속도와 heading으로 위치를 이어 붙임.
 *
 * ```
 * scenario("좌회전") {
 *     start()
 *     straight(seconds = 20, speedKmh = 40f)
 *     signal(TurnSignalState.LEFT)
 *     straight(seconds = 3, speedKmh = 20f)
 *     turn(angleDeg = -90f, seconds = 6, speedKmh = 20f)
 *     signal(TurnSignalState.OFF)
 *     end()
 * }
 * ```
 */
class ScenarioBuilder(
    private var latitude: Double = 37.5665,
    private var longitude: Double = 126.9780,
    private var headingDeg: Float = 0f,
) {
    private var nowMs = 0L
    private val locations = mutableListOf<LocationSample>()
    private val signals = mutableListOf<SignalSample>()
    private val vehicleStates = mutableListOf<VehicleStateSample>()

    /** 시동 on + D단 */
    fun start() {
        vehicleStates += VehicleStateSample(nowMs, ignitionOn = true, gear = Gear.D)
        record(0f)
    }

    fun straight(seconds: Int, speedKmh: Float) = repeat(seconds) { step(speedKmh, 0f) }

    /** [angleDeg]만큼 [seconds]초에 걸쳐 고르게 회전. 양수는 우회전, 음수는 좌회전 */
    fun turn(angleDeg: Float, seconds: Int, speedKmh: Float) = repeat(seconds) { step(speedKmh, angleDeg / seconds) }

    fun stop(seconds: Int) = repeat(seconds) { step(0f, 0f, hasHeading = false) }

    fun signal(state: TurnSignalState) {
        signals += SignalSample(nowMs, state)
    }

    fun gear(gear: Gear) {
        vehicleStates += VehicleStateSample(nowMs, ignitionOn = true, gear = gear)
    }

    /** P단 후 시동 off */
    fun end() {
        gear(Gear.P)
        stop(1)
        vehicleStates += VehicleStateSample(nowMs, ignitionOn = false, gear = Gear.P)
    }

    fun build(name: String) = Scenario(name, locations.toList(), signals.toList(), vehicleStates.toList())

    private fun step(speedKmh: Float, headingChangeDeg: Float, hasHeading: Boolean = true) {
        nowMs += 1_000L
        headingDeg = ((headingDeg + headingChangeDeg) % 360f + 360f) % 360f
        val distanceM = speedKmh / 3.6
        val rad = Math.toRadians(headingDeg.toDouble())
        latitude += distanceM * cos(rad) / METERS_PER_DEG_LAT
        longitude += distanceM * sin(rad) / (METERS_PER_DEG_LAT * cos(Math.toRadians(latitude)))
        record(speedKmh, hasHeading)
    }

    private fun record(speedKmh: Float, hasHeading: Boolean = true) {
        locations += LocationSample(
            timestampMs = nowMs,
            latitude = latitude,
            longitude = longitude,
            speedKmh = speedKmh,
            headingDeg = if (hasHeading) headingDeg else null,
        )
    }

    private companion object {
        const val METERS_PER_DEG_LAT = 111_320.0
    }
}

fun scenario(name: String, block: ScenarioBuilder.() -> Unit): Scenario =
    ScenarioBuilder().apply(block).build(name)
