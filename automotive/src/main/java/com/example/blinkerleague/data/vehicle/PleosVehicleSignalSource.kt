package com.example.blinkerleague.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.blinkerleague.AppLog
import com.example.blinkerleague.domain.model.Availability
import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.CapabilityReport
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.HeadlightSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.TurnSignalState
import com.example.blinkerleague.domain.model.VehicleStateSample
import kotlin.coroutines.resume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 실제 차량 신호 공급원: Pleos Vehicle SDK 2.3.2(방향지시등·핸들 각도·기어·브레이크·하향등) + CarPropertyManager(속도).
 *
 * API·권한은 SDK 클래스(javap)와 에뮬레이터 실측으로 확인함 (docs/platform-capabilities.md):
 * - 방향지시등 `TurnSignal.registerTurnSignalState` — `pleos.car.permission.CAR_LIGHTS`
 * - 핸들 각도 `SteeringWheel.registerSteeringWheelAngle` (도, 에뮬레이터에서 오른쪽이 음수) — `CAR_DRIVE`
 * - 기어 `Gear.registerGearSelection` (capability 확인 API 없음 → get 성공 여부로 판단) — `CAR_DRIVE`
 * - 브레이크 페달 `Brake.registerBrakePedalStatus` (값이 바뀔 때만 옴) — `CAR_DRIVE`
 * - 하향등 `Light.registerLowBeamLightState` (`ON`만 켜짐, `DAYTIME_RUNNING`은 꺼짐으로 봄) — `CAR_LIGHTS`
 * - 속도 CarProperty `PERF_VEHICLE_SPEED` (m/s, 약 10Hz) — `android.car.permission.CAR_SPEED`(런타임)
 * - 시동 상태 API는 없음 → [VehicleStateSample.ignitionOn]은 항상 null
 *
 * 리스너는 등록 직후 현재 값을 한 번 보냄. 미지원이면 `onFailed`가 와서 Flow를 끝냄.
 * 백그라운드에서도 받으려면 포그라운드 서비스 안에서 수집해야 함 (일반 백그라운드는 약 10초 뒤 동결).
 *
 * @param clock 샘플 타임스탬프용 현재 시각 (epoch ms). 다른 공급원과 같은 기준
 * @param speedGranted 속도 권한 허용 여부 Flow ([com.example.blinkerleague.data.permission.PermissionRepository]).
 *   수집 중에 권한이 허용되면 그때 속도를 구독함. null이면 구독 시점에 한 번만 확인
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PleosVehicleSignalSource(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val speedGranted: Flow<Boolean>? = null,
) : VehicleSignalSource {

    private val appContext = context.applicationContext

    // initialize()에는 완료 콜백이 없음. 첫 capability 확인이 실패하면 한 번 다시 시도함
    private val vehicle: Vehicle by lazy { Vehicle(appContext).also { it.initialize() } }

    private val propertyManager: CarPropertyManager? by lazy {
        runCatching { Car.createCar(appContext)?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager }
            .onFailure { AppLog.w(TAG, "CarPropertyManager 연결 실패", it) }
            .getOrNull()
    }

    override suspend fun capabilities(): CapabilityReport = CapabilityReport(
        turnSignal = check { ok, fail -> vehicle.getTurnSignal().checkTurnSignalStateCapabilities({ ok() }, fail) },
        steering = check { ok, fail -> vehicle.getSteeringWheel().checkSteeringWheelAngleCapabilities({ _, _ -> ok() }, fail) },
        gear = check { ok, fail -> vehicle.getGear().getCurrentGearSelectionState({ ok() }, fail) },
        speed = speedAvailability(),
        brake = check { ok, fail -> vehicle.getBrake().checkBrakePedalStatusCapabilities({ ok() }, fail) },
        headlight = check { ok, fail -> vehicle.getLight().checkLowBeamLightStateCapabilities({ _, _ -> ok() }, fail) },
    )

    private fun speedGranted() =
        appContext.checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED

    private fun speedAvailability(): Availability = when {
        !speedGranted() -> Availability.NO_PERMISSION
        runCatching { propertyManager?.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) }.getOrNull() == null ->
            Availability.UNSUPPORTED
        else -> Availability.SUPPORTED
    }

    /** SDK의 (onComplete, onFailed) 확인 API 하나를 [Availability]로. 실패하면 1초 뒤 한 번 더 시도함 */
    private suspend fun check(call: (ok: () -> Unit, fail: (Exception) -> Unit) -> Unit): Availability {
        repeat(CHECK_ATTEMPTS) { attempt ->
            val result = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    try {
                        call({ if (cont.isActive) cont.resume(Availability.SUPPORTED) }) { e ->
                            AppLog.i(TAG, "capability 실패: $e")
                            if (cont.isActive) cont.resume(Availability.UNSUPPORTED)
                        }
                    } catch (e: SecurityException) {
                        if (cont.isActive) cont.resume(Availability.NO_PERMISSION)
                    }
                }
            }
            if (result == Availability.SUPPORTED || result == Availability.NO_PERMISSION) return result
            if (attempt < CHECK_ATTEMPTS - 1) delay(CHECK_RETRY_MS)
        }
        return Availability.UNSUPPORTED
    }

    override val turnSignals: Flow<SignalSample> = callbackFlow {
        val turnSignal = vehicle.getTurnSignal()
        val listener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                state?.let { trySend(SignalSample(clock(), it.toDomain())) }
            }
            override fun onFailed(exception: Exception) {
                AppLog.w(TAG, "방향지시등 미지원: $exception")
                close()
            }
        }
        turnSignal.registerTurnSignalState(listener)
        awaitClose { turnSignal.unregisterTurnSignalState(listener) }
    }

    override val steeringAngles: Flow<SteeringSample> = callbackFlow {
        val steering = vehicle.getSteeringWheel()
        val listener = object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(angle: Float?) {
                angle?.let { trySend(SteeringSample(clock(), it)) }
            }
            override fun onFailed(exception: Exception) {
                AppLog.w(TAG, "핸들 각도 미지원: $exception")
                close()
            }
        }
        steering.registerSteeringWheelAngle(listener)
        awaitClose { steering.unregisterSteeringWheelAngle(listener) }
    }

    override val vehicleStates: Flow<VehicleStateSample> = callbackFlow {
        val gear = vehicle.getGear()
        val listener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                trySend(VehicleStateSample(clock(), ignitionOn = null, gear = gear.toDomain()))
            }
            override fun onFailed(exception: Exception) {
                AppLog.w(TAG, "기어 미지원: $exception")
                close()
            }
        }
        gear.registerGearSelection(listener)
        awaitClose { gear.unregisterGearSelection(listener) }
    }

    override val brakes: Flow<BrakeSample> = callbackFlow {
        val brake = vehicle.getBrake()
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
                pressed?.let { trySend(BrakeSample(clock(), it)) }
            }
            override fun onFailed(exception: Exception) {
                AppLog.w(TAG, "브레이크 미지원: $exception")
                close()
            }
        }
        if (!register("브레이크") { brake.registerBrakePedalStatus(listener) }) return@callbackFlow
        awaitClose { brake.unregisterBrakePedalStatus(listener) }
    }

    override val headlights: Flow<HeadlightSample> = callbackFlow {
        val light = vehicle.getLight()
        val listener = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) {
                state?.let { trySend(HeadlightSample(clock(), on = it == LightState.ON)) }
            }
            override fun onFailed(exception: Exception) {
                AppLog.w(TAG, "하향등 미지원: $exception")
                close()
            }
        }
        if (!register("하향등") { light.registerLowBeamLightState(listener) }) return@callbackFlow
        awaitClose { light.unregisterLowBeamLightState(listener) }
    }

    /** 리스너 등록. 권한이 없어 SecurityException이 나면 수집을 멈추지 않고 이 Flow만 끝냄 */
    private fun ProducerScope<*>.register(name: String, block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: SecurityException) {
            AppLog.w(TAG, "$name 권한 없음: $e")
            close()
            false
        }

    /** 권한이 생기면 구독하고, 없어지면 멈춤. 권한이 없는 동안은 아무것도 보내지 않음(끝나지 않음) */
    override val speeds: Flow<SpeedSample> = (speedGranted ?: flowOf(true))
        .distinctUntilChanged()
        .flatMapLatest { granted -> if (granted) speedUpdates else emptyFlow() }

    private val speedUpdates: Flow<SpeedSample> = callbackFlow {
        val manager = propertyManager
        if (manager == null || !speedGranted()) {
            AppLog.w(TAG, "속도를 받을 수 없음 (권한 ${speedGranted()}, 서비스 ${manager != null})")
            close()
            return@callbackFlow
        }
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                (value.value as? Float)?.let { mps -> trySend(SpeedSample(clock(), mps * MPS_TO_KMH)) }
            }
            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                AppLog.w(TAG, "속도 오류 이벤트 area=$areaId")
            }
        }
        val registered = runCatching {
            manager.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
        }.getOrDefault(false)
        if (!registered) {
            close()
            return@callbackFlow
        }
        AppLog.i(TAG, "속도 구독 시작")
        awaitClose { manager.unregisterCallback(callback) }
    }

    private fun TurnSignalValue.toDomain() = when (this) {
        TurnSignalValue.LEFT -> TurnSignalState.LEFT
        TurnSignalValue.RIGHT -> TurnSignalState.RIGHT
        TurnSignalValue.OFF -> TurnSignalState.OFF
    }

    private fun VehicleGear?.toDomain() = when (this) {
        VehicleGear.GEAR_PARK -> Gear.P
        VehicleGear.GEAR_REVERSE -> Gear.R
        VehicleGear.GEAR_NEUTRAL -> Gear.N
        VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3, VehicleGear.GEAR_4,
        VehicleGear.GEAR_5, VehicleGear.GEAR_6, VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9,
        -> Gear.D
        VehicleGear.GEAR_UNKNOWN, null -> Gear.UNKNOWN
    }

    private companion object {
        const val TAG = "PleosVehicleSource"
        const val MPS_TO_KMH = 3.6f
        const val CHECK_ATTEMPTS = 2
        const val CHECK_TIMEOUT_MS = 3_000L
        const val CHECK_RETRY_MS = 1_000L
    }
}
