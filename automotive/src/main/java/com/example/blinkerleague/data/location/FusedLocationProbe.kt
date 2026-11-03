package com.example.blinkerleague.data.location

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import com.example.blinkerleague.data.debug.ProbeLog

/**
 * 조사용: Pleos FusedLocation SDK 1.1.3으로 위치를 받아 [ProbeLog](LocationTest)에 남김.
 *
 * API(문서 FusedLocation > Initialization and release·Location data access, SDK javap):
 * `FusedLocation(Context)`, `isSupported(): Boolean`, `initialize()`, `release()`,
 * `getLastKnownLocation(FusedLocationListener)`, `registerFusedLocationCallback(FusedLocationListener)`,
 * `unregisterFusedLocationCallback(…)`, `addProximityAlert(ProximityAlertRequest, ProximityAlertListener)`,
 * `removeProximityAlert(alertId: String)`. 권한은 모두 `pleos.car.permission.FUSED_LOCATION`(기기 prot=dangerous).
 * `ProximityAlertRequest(latitude, longitude, radiusMeters, transitionTypes)` → 원형 영역.
 */
class FusedLocationProbe(private val context: Context, private val log: ProbeLog) {

    private val fused = FusedLocation(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var pollMs = 0L
    private val alertIds = mutableListOf<String>()

    private fun log(name: String, value: String) = log.log(SOURCE, name, value)

    private fun format(l: LocationData?): String = l?.let {
        "lat=${it.latitude} lng=${it.longitude} alt=${it.altitude} heading=${it.heading} " +
            "confidence=${it.confidence} ts=${it.timestampMillis}"
    } ?: "null"

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) = log("onChanged", format(location))
        override fun onError(exception: Exception) = log("onError", exception.toString())
    }

    private val lastKnown = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) = log("getLastKnownLocation", format(location))
        override fun onError(exception: Exception) = log("getLastKnownLocation.onError", exception.toString())
    }

    fun start(via: String, pollIntervalMs: Long = 5_000L) {
        if (started) return
        started = true
        log("start", "via=$via")
        val granted = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
        log("permission", "$PERMISSION granted=$granted")
        val supported = runCatching { fused.isSupported() }
        log("isSupported", supported.fold({ it.toString() }, { "failed: $it" }))
        if (supported.getOrNull() != true) return
        runCatching { fused.initialize() }.onFailure { log("initialize.failed", it.toString()) }
        log("initialize", "returned")
        runCatching { fused.getLastKnownLocation(lastKnown) }.onFailure { log("getLastKnownLocation.failed", it.toString()) }
        runCatching { fused.registerFusedLocationCallback(listener) }
            .onSuccess { log("registerFusedLocationCallback", "done") }
            .onFailure { log("registerFusedLocationCallback.failed", it.toString()) }
        pollMs = pollIntervalMs
        if (pollMs > 0) handler.postDelayed(poll, pollMs)
    }

    /** 리스너와 별개로 마지막 위치도 주기적으로 확인 */
    private val poll = object : Runnable {
        override fun run() {
            runCatching { fused.getLastKnownLocation(lastKnown) }
            if (pollMs > 0) handler.postDelayed(this, pollMs)
        }
    }

    fun addProximityAlert(latitude: Double, longitude: Double, radiusMeters: Float) {
        val request = ProximityAlertRequest(
            latitude, longitude, radiusMeters, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT),
        )
        log("addProximityAlert", request.toString())
        runCatching {
            fused.addProximityAlert(
                request,
                object : ProximityAlertListener {
                    override fun onRegistered(alertId: String) {
                        alertIds += alertId
                        log("onRegistered", "alertId=$alertId")
                    }
                    override fun onTransition(event: ProximityAlertEvent) =
                        log("onTransition", "alertId=${event.alertId} transition=${event.transition}")
                    override fun onError(exception: Exception) = log("proximity.onError", exception.toString())
                },
            )
        }.onFailure { log("addProximityAlert.failed", it.toString()) }
    }

    fun stop() {
        if (!started) return
        started = false
        pollMs = 0
        handler.removeCallbacks(poll)
        alertIds.forEach { runCatching { fused.removeProximityAlert(it) } }
        runCatching { fused.unregisterFusedLocationCallback(listener) }
        runCatching { fused.release() }
        log("release", "")
    }

    private companion object {
        const val SOURCE = "Fused"
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}
