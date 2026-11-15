package com.example.blinkerleague.data.scenario

import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.TurnSignalState
import com.example.blinkerleague.domain.model.VehicleStateSample
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 시나리오를 동작 단위로 만드는 빌더. 위치는 1초마다 한 점씩, 속도와 heading으로 위치를 이어 붙임.
 * 핸들 각도는 [turn] 동안 꺾었다가 끝나면 0으로 돌아옴. 부호는 Vehicle SDK(에뮬레이터 실측)와 같게 오른쪽이 음수.
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
    private val steering = mutableListOf<SteeringSample>()
    private val signals = mutableListOf<SignalSample>()
    private val vehicleStates = mutableListOf<VehicleStateSample>()

    /** 시동 on + D단 */
    fun start() {
        vehicleStates += VehicleStateSample(nowMs, ignitionOn = true, gear = Gear.D)
        record(0f)
    }

    fun straight(seconds: Int, speedKmh: Float) = repeat(seconds) { step(speedKmh, 0f) }

    /**
     * 진행 방향을 [angleDeg]만큼 [seconds]초에 걸쳐 고르게 바꾸는 회전. 양수는 우회전, 음수는 좌회전.
     * 핸들은 첫 1초·마지막 1초는 [steeringDeg]의 40%, 나머지는 [steeringDeg]만큼 꺾음 (3초 이상일 때)
     */
    fun turn(angleDeg: Float, seconds: Int, speedKmh: Float, steeringDeg: Float = 120f) {
        val side = if (angleDeg >= 0) -1f else 1f // 우회전 = 핸들 음수 (SDK 부호)
        repeat(seconds) { i ->
            val edge = seconds >= 3 && (i == 0 || i == seconds - 1)
            steer(side * abs(steeringDeg) * (if (edge) 0.4f else 1f))
            step(speedKmh, angleDeg / seconds)
        }
        steer(0f)
    }

    /** 진행 방향은 그대로 두고 핸들만 [steeringDeg]로 [seconds]초 꺾었다 돌아옴 (차로 변경, 흔들림 등) */
    fun wiggle(steeringDeg: Float, seconds: Int, speedKmh: Float) {
        steer(steeringDeg)
        straight(seconds, speedKmh)
        steer(0f)
    }

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

    fun build(name: String) =
        Scenario(name, locations.toList(), steering.toList(), signals.toList(), vehicleStates.toList())

    private fun steer(angleDeg: Float) {
        steering += SteeringSample(nowMs, angleDeg)
    }

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
