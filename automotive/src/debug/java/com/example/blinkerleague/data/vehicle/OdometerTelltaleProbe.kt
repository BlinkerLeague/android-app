package com.example.blinkerleague.data.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import com.example.blinkerleague.data.debug.ProbeLog

/**
 * 조사용: Vehicle SDK 2.3.2의 Odometer(트립 주행 거리·시간)와 Safety 경고등(telltale)을 [ProbeLog](VehicleTest)에 남김.
 *
 * API(SDK javap):
 * - Odometer (`pleos.car.permission.CAR_DRIVE`): `registerDistanceDriven`, `registerTripCurrentDrivingDistance`,
 *   `registerTripCumulativeDrivingDistance` → `DistanceDrivenListener.onDistanceDrivenUpdated(Float?)`,
 *   `registerTripCurrentDrivingTime`, `registerTripCumulativeDrivingTime` → `DrivingTimeListener.onDrivingTimeUpdated(Int?)`,
 *   각각의 `check…Capabilities`, `get…`
 * - Safety 경고등: `getTelltalesInfo`(`CAR_SAFETY`), `checkTelltalesCapabilities`·`registerTelltales`(`PRIVATE_CAR_SAFETY`)
 *   → `TelltaleInfoListener.onTelltaleInfoUpdated(Telltales)`. Light 도메인에는 비상등 API가 없음
 */
class OdometerTelltaleProbe(context: Context, private val log: ProbeLog) {

    private val vehicle = Vehicle(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var pollMs = 0L

    private fun log(name: String, value: String) = log.log(SOURCE, name, value)

    private fun distanceListener(name: String) = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(distance: Float?) = log(name, distance.toString())
        override fun onFailed(exception: Exception) = log("$name.onFailed", exception.toString())
    }

    private fun timeListener(name: String) = object : DrivingTimeListener {
        override fun onDrivingTimeUpdated(time: Int?) = log(name, time.toString())
        override fun onFailed(exception: Exception) = log("$name.onFailed", exception.toString())
    }

    private val odometer = distanceListener("onDistanceDriven(Odometer)")
    private val tripCurrentDistance = distanceListener("onTripCurrentDrivingDistance")
    private val tripCumulativeDistance = distanceListener("onTripCumulativeDrivingDistance")
    private val tripCurrentTime = timeListener("onTripCurrentDrivingTime")
    private val tripCumulativeTime = timeListener("onTripCumulativeDrivingTime")

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) =
            log("onTelltaleInfoUpdated", telltales?.let(::turnTelltales) ?: "null")
        override fun onFailed(exception: Exception) = log("Telltales.onFailed", exception.toString())
    }

    /** 경고등 중 방향지시등 관련만 (전체는 [start]에서 한 번 남김) */
    private fun turnTelltales(t: Telltales): String {
        val turn = t.telltales.filter {
            it.type == TelltaleType.TURN_SIGNAL_LEFT || it.type == TelltaleType.TURN_SIGNAL_RIGHT
        }
        return "turn=" + turn.joinToString(prefix = "[", postfix = "]") { "${it.type.name}:${it.state.name}" } +
            " total=${t.telltales.size}"
    }

    fun start(via: String, pollIntervalMs: Long = 5_000L) {
        if (started) return
        started = true
        log("start", "via=$via")
        vehicle.initialize()
        val o = vehicle.getOdometer()
        fun cap(name: String) = { c: CapabilityConfig -> log(name, ProbeLog.dump(c)) }
        fun fail(name: String) = { e: Exception -> log("$name.onFailed", e.toString()) }
        o.checkCurrentDistanceDrivenCapabilities(cap("checkCurrentDistanceDrivenCapabilities"), fail("checkCurrentDistanceDrivenCapabilities"))
        o.checkTripCurrentDrivingDistanceCapabilities(cap("checkTripCurrentDrivingDistanceCapabilities"), fail("checkTripCurrentDrivingDistanceCapabilities"))
        o.checkTripCumulativeDrivingDistanceCapabilities(cap("checkTripCumulativeDrivingDistanceCapabilities"), fail("checkTripCumulativeDrivingDistanceCapabilities"))
        o.checkTripCurrentDrivingTimeCapabilities(cap("checkTripCurrentDrivingTimeCapabilities"), fail("checkTripCurrentDrivingTimeCapabilities"))
        o.checkTripCumulativeDrivingTimeCapabilities(cap("checkTripCumulativeDrivingTimeCapabilities"), fail("checkTripCumulativeDrivingTimeCapabilities"))
        o.registerDistanceDriven(odometer)
        o.registerTripCurrentDrivingDistance(tripCurrentDistance)
        o.registerTripCumulativeDrivingDistance(tripCumulativeDistance)
        o.registerTripCurrentDrivingTime(tripCurrentTime)
        o.registerTripCumulativeDrivingTime(tripCumulativeTime)
        log("registerOdometer", "done")

        val s = vehicle.getSafety()
        s.checkTelltalesCapabilities(cap("checkTelltalesCapabilities"), fail("checkTelltalesCapabilities"))
        s.getTelltalesInfo({ log("getTelltalesInfo", it?.let { t -> "${turnTelltales(t)} all=${ProbeLog.dump(t)}" } ?: "null") }, fail("getTelltalesInfo"))
        s.registerTelltales(telltaleListener)
        log("registerTelltales", "done")

        pollMs = pollIntervalMs
        if (pollMs > 0) handler.postDelayed(poll, pollMs)
    }

    /** 리스너가 변화 때만 오는지 확인하려고 get도 주기적으로 부름 */
    private val poll = object : Runnable {
        override fun run() {
            val o = vehicle.getOdometer()
            o.getTripCurrentDrivingDistance({ log("getTripCurrentDrivingDistance", it.toString()) }, { log("getTripCurrentDrivingDistance.onFailed", it.toString()) })
            o.getCurrentDistanceDriven({ log("getCurrentDistanceDriven", it.toString()) }, { log("getCurrentDistanceDriven.onFailed", it.toString()) })
            o.getTripCurrentDrivingTime({ log("getTripCurrentDrivingTime", it.toString()) }, { log("getTripCurrentDrivingTime.onFailed", it.toString()) })
            if (pollMs > 0) handler.postDelayed(this, pollMs)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        pollMs = 0
        handler.removeCallbacks(poll)
        val o = vehicle.getOdometer()
        o.unregisterDistanceDriven(odometer)
        o.unregisterTripCurrentDrivingDistance(tripCurrentDistance)
        o.unregisterTripCumulativeDrivingDistance(tripCumulativeDistance)
        o.unregisterTripCurrentDrivingTime(tripCurrentTime)
        o.unregisterTripCumulativeDrivingTime(tripCumulativeTime)
        vehicle.getSafety().unregisterTelltales(telltaleListener)
        vehicle.release()
        log("release", "")
    }

    private companion object {
        const val SOURCE = "Vehicle"
    }
}
