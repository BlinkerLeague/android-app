package com.example.blinkerleague.data.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import com.example.blinkerleague.data.debug.ProbeLog

/**
 * 조사용: Pleos Vehicle SDK 2.3.2로 방향지시등·조향각·기어 콜백을 받아 [ProbeLog](VehicleTest)에 남김.
 *
 * API는 SDK 클래스(javap)로 확인함:
 * - `Vehicle(Context)`, `initialize()`, `getTurnSignal()`, `getSteeringWheel()`, `getGear()`, `release()`
 * - TurnSignal: `checkTurnSignalStateCapabilities`, `getCurrentTurnSignalState`, `registerTurnSignalState` (CAR_LIGHTS)
 * - SteeringWheel: `checkSteeringWheelAngleCapabilities`, `getCurrentSteeringWheelAngle`, `registerSteeringWheelAngle` (CAR_DRIVE)
 * - Gear: `getCurrentGearSelectionState`, `registerGearSelection` (CAR_DRIVE). capability 확인 API는 없음
 *
 * `initialize()`에는 연결 완료 콜백이 없어서, capability 확인이 실패하면 1초 간격으로 다시 시도한 뒤 리스너를 등록함.
 */
class VehicleProbe(context: Context, private val log: ProbeLog) {

    private val vehicle = Vehicle(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    private fun log(name: String, value: String) = log.log(SOURCE, name, value)

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) =
            log("onTurnSignalStateUpdated", state?.let { "${it.name}(id=${it.id})" } ?: "null")
        override fun onFailed(exception: Exception) = log("TurnSignal.onFailed", exception.toString())
    }

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = log("onSteeringWheelAngleUpdated", angle.toString())
        override fun onFailed(exception: Exception) = log("SteeringWheel.onFailed", exception.toString())
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = log("onSelected(Gear)", gear?.name ?: "null")
        override fun onFailed(exception: Exception) = log("Gear.onFailed", exception.toString())
    }

    fun start(via: String) {
        if (started) return
        started = true
        log("start", "via=$via")
        vehicle.initialize()
        log("initialize", "returned")
        checkTurnSignal(attempt = 1)
        checkSteering(attempt = 1)
        // Gear에는 capability 확인 API가 없음 → 현재 값 확인 후 바로 등록
        vehicle.getGear().getCurrentGearSelectionState(
            { log("getCurrentGearSelectionState", it?.name ?: "null") },
            { log("getCurrentGearSelectionState.onFailed", it.toString()) },
        )
        vehicle.getGear().registerGearSelection(gearListener)
        log("registerGearSelection", "done")
    }

    fun stop() {
        if (!started) return
        started = false
        vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
        vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
        vehicle.getGear().unregisterGearSelection(gearListener)
        vehicle.release()
        log("release", "")
    }

    private fun checkTurnSignal(attempt: Int) {
        vehicle.getTurnSignal().checkTurnSignalStateCapabilities(
            { config ->
                log("checkTurnSignalStateCapabilities", "attempt=$attempt ${ProbeLog.dump(config)}")
                registerTurnSignal()
            },
            { e ->
                log("checkTurnSignalStateCapabilities.onFailed", "attempt=$attempt $e")
                if (attempt < MAX_ATTEMPTS) handler.postDelayed({ checkTurnSignal(attempt + 1) }, RETRY_MS) else registerTurnSignal()
            },
        )
    }

    private fun registerTurnSignal() {
        vehicle.getTurnSignal().getCurrentTurnSignalState(
            { log("getCurrentTurnSignalState", it?.let { v -> "${v.name}(id=${v.id})" } ?: "null") },
            { log("getCurrentTurnSignalState.onFailed", it.toString()) },
        )
        vehicle.getTurnSignal().registerTurnSignalState(turnSignalListener)
        log("registerTurnSignalState", "done")
    }

    private fun checkSteering(attempt: Int) {
        vehicle.getSteeringWheel().checkSteeringWheelAngleCapabilities(
            { config, bounds ->
                log("checkSteeringWheelAngleCapabilities", "attempt=$attempt ${ProbeLog.dump(config)} bounds=$bounds")
                registerSteering()
            },
            { e ->
                log("checkSteeringWheelAngleCapabilities.onFailed", "attempt=$attempt $e")
                if (attempt < MAX_ATTEMPTS) handler.postDelayed({ checkSteering(attempt + 1) }, RETRY_MS) else registerSteering()
            },
        )
    }

    private fun registerSteering() {
        vehicle.getSteeringWheel().getCurrentSteeringWheelAngle(
            { log("getCurrentSteeringWheelAngle", it.toString()) },
            { log("getCurrentSteeringWheelAngle.onFailed", it.toString()) },
        )
        vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
        log("registerSteeringWheelAngle", "done")
    }

    private companion object {
        const val SOURCE = "Vehicle"
        const val MAX_ATTEMPTS = 5
        const val RETRY_MS = 1_000L
    }
}
