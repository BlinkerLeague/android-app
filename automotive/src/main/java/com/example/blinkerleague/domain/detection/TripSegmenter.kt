package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.TripConfig
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.VehicleStateSample

sealed interface TripBoundary {
    val atMs: Long

    data class Started(override val atMs: Long) : TripBoundary
    data class Ended(override val atMs: Long, val reason: EndReason) : TripBoundary
}

enum class EndReason { IGNITION_OFF, PARKED }

/**
 * 기어(와 알 수 있으면 시동) 상태로 운행의 시작과 끝을 구분하는 상태 기계.
 *
 * - 시작: 기어가 P가 아닌 단(D·R·N)이 될 때, 또는 시동이 꺼져 있다가 켜질 때(시동을 알 수 있을 때만)
 * - 끝: P단이 [TripConfig.parkEndMinutes]분 이상 유지될 때(끝 시각은 P단에 들어간 시각), 또는 시동 off(알 수 있을 때만)
 *
 * Pleos Vehicle SDK에는 시동 상태 API가 없어서 실제 공급원은 `ignitionOn = null`을 줌 → 기어만으로 구분함.
 * 시동 off(false)가 명시된 샘플은 운행을 끝내고, 그 뒤에는 기어가 바뀌어도 시작하지 않음.
 *
 * 시간 함수를 직접 부르지 않음. 상태 변화가 없어도 P단 시간을 확인할 수 있도록 호출하는 쪽에서 [onTick]으로 현재 시각을 넣어줌.
 * 기어를 알 수 없으면([Gear.UNKNOWN]) 운행을 시작하지 않음(시동을 알 수 있으면 시동으로만 구분).
 */
class TripSegmenter(private val config: TripConfig) {

    var inTrip: Boolean = false
        private set

    private var ignitionOn: Boolean? = null
    private var parkedSinceMs: Long? = null

    fun onVehicleState(sample: VehicleStateSample): TripBoundary? {
        val wasIgnitionOn = ignitionOn
        ignitionOn = sample.ignitionOn
        val t = sample.timestampMs

        if (!inTrip) {
            val ignitionTurnedOn = sample.ignitionOn == true && wasIgnitionOn != true
            val leftPark = sample.ignitionOn != false && sample.gear.isDriving()
            if (ignitionTurnedOn || leftPark) {
                inTrip = true
                parkedSinceMs = if (sample.gear == Gear.P) t else null
                return TripBoundary.Started(t)
            }
            return null
        }

        if (sample.ignitionOn == false) return end(t, EndReason.IGNITION_OFF)

        parkedSinceMs = if (sample.gear == Gear.P) parkedSinceMs ?: t else null
        return onTick(t)
    }

    fun onTick(nowMs: Long): TripBoundary? {
        val parkedSince = parkedSinceMs ?: return null
        if (inTrip && nowMs - parkedSince >= config.parkEndMs) return end(parkedSince, EndReason.PARKED)
        return null
    }

    private fun end(atMs: Long, reason: EndReason): TripBoundary {
        inTrip = false
        parkedSinceMs = null
        return TripBoundary.Ended(atMs, reason)
    }

    private fun Gear.isDriving() = this == Gear.D || this == Gear.R || this == Gear.N
}
