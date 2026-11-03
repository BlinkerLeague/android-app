package com.example.blinkerleague.data.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.blinkerleague.data.debug.ProbeLog

/**
 * 조사용: android.car의 CarPropertyManager로 차량 속도·기어 등을 구독해 [ProbeLog](SpeedTest)에 남김.
 *
 * - 라이브러리: `useLibrary("android.car")` (Android Developers, Add support for Android Automotive OS to your parked app)
 * - API(android-34 `android.car.jar`, javap): `Car.createCar(Context)`, `getCarManager(Car.PROPERTY_SERVICE)`,
 *   `CarPropertyManager.getPropertyList()`, `getCarPropertyConfig(int)`, `registerCallback(cb, propId, rate)`
 * - 권한: `Car.PERMISSION_SPEED` = android.car.permission.CAR_SPEED (기기 prot=dangerous → 런타임 허용 필요),
 *   `Car.PERMISSION_POWERTRAIN` = android.car.permission.CAR_POWERTRAIN (prot=normal)
 */
class CarPropertyProbe(private val context: Context, private val log: ProbeLog) {

    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private fun log(name: String, value: String) = log.log(SOURCE, name, value)

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            log(
                VehiclePropertyIds.toString(value.propertyId),
                "value=${ProbeLog.dump(value.value)} status=${value.status} ts=${value.timestamp}",
            )
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            log("onErrorEvent", "${VehiclePropertyIds.toString(propertyId)} area=$areaId")
        }
    }

    fun start(via: String) {
        if (car != null) return
        log("start", "via=$via")
        for (permission in listOf(Car.PERMISSION_SPEED, Car.PERMISSION_POWERTRAIN)) {
            val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            log("permission", "$permission granted=$granted")
        }
        val created = runCatching { Car.createCar(context.applicationContext) }
            .onFailure { log("createCar.failed", it.toString()) }.getOrNull() ?: return
        car = created
        val cpm = runCatching { created.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager }
            .onFailure { log("getCarManager.failed", it.toString()) }.getOrNull() ?: return
        manager = cpm

        val listed = runCatching { cpm.propertyList.map { it.propertyId }.toSet() }
            .onFailure { log("getPropertyList.failed", it.toString()) }.getOrDefault(emptySet())
        log("getPropertyList", "count=${listed.size}")

        for ((id, rate) in PROPERTIES) {
            val name = VehiclePropertyIds.toString(id)
            val config = runCatching { cpm.getCarPropertyConfig(id) }
            log(
                "config",
                "$name inList=${id in listed} " + config.fold({ it?.toString() ?: "null" }, { "failed: $it" }),
            )
            val registered = runCatching { cpm.registerCallback(callback, id, rate) }
            log("registerCallback", "$name rate=$rate " + registered.fold({ "result=$it" }, { "failed: $it" }))
        }
    }

    fun stop() {
        manager?.unregisterCallback(callback)
        car?.disconnect()
        car = null
        manager = null
        log("stop", "")
    }

    private companion object {
        const val SOURCE = "CarProperty"

        /** 속성 ID → 구독 주기. 연속값은 가장 빠르게, 상태값은 변할 때만 */
        val PROPERTIES = listOf(
            VehiclePropertyIds.PERF_VEHICLE_SPEED to CarPropertyManager.SENSOR_RATE_FASTEST,
            VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY to CarPropertyManager.SENSOR_RATE_FASTEST,
            VehiclePropertyIds.GEAR_SELECTION to CarPropertyManager.SENSOR_RATE_ONCHANGE,
            VehiclePropertyIds.CURRENT_GEAR to CarPropertyManager.SENSOR_RATE_ONCHANGE,
            VehiclePropertyIds.PERF_STEERING_ANGLE to CarPropertyManager.SENSOR_RATE_FASTEST,
            VehiclePropertyIds.TURN_SIGNAL_STATE to CarPropertyManager.SENSOR_RATE_ONCHANGE,
        )
    }
}
