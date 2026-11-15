package com.example.blinkerleague.data.debug

import android.content.Context
import android.content.Intent
import com.example.blinkerleague.data.location.FusedLocationProbe
import com.example.blinkerleague.data.navi.NaviEventLogger
import com.example.blinkerleague.data.vehicle.CarPropertyProbe
import com.example.blinkerleague.data.vehicle.OdometerTelltaleProbe
import com.example.blinkerleague.data.vehicle.VehicleProbe

/**
 * 조사용 프로브 모음 (디버그 빌드에서만 씀). 같은 프로세스에서 한 번만 시작됨.
 * - vehicle: Pleos Vehicle SDK 방향지시등·조향각·기어 → Logcat VehicleTest
 * - odometer: Vehicle SDK 트립 주행 거리·시간, Safety 경고등(방향지시등) → Logcat VehicleTest
 * - speed: android.car CarPropertyManager 속도·기어 등 → Logcat SpeedTest
 * - navi: NaviHelper 이벤트 → Logcat NaviTest
 * - location: Pleos FusedLocation 위치·근접 알림 → Logcat LocationTest
 *
 * 시작 경로(via)로 실험 상태를 구분함: activity(앱 프로세스에서 등록, 서비스 없음) / service(포그라운드 서비스에서 등록)
 */
class DebugProbes(context: Context) {
    private val appContext = context.applicationContext

    private val vehicleLog by lazy { ProbeLog("VehicleTest") }
    val vehicle by lazy { VehicleProbe(appContext, vehicleLog) }
    val odometer by lazy { OdometerTelltaleProbe(appContext, vehicleLog) }
    val carProperty by lazy { CarPropertyProbe(appContext, ProbeLog("SpeedTest")) }
    val navi by lazy { NaviEventLogger(appContext, ProbeLog("NaviTest")) }
    val location by lazy { FusedLocationProbe(appContext, ProbeLog("LocationTest")) }

    /** 조사 도구 자체의 이벤트(서비스 시작 시도 등)를 VehicleTest의 [Probe] 출처로 남김 */
    fun event(name: String, value: String) = vehicleLog.log("Probe", name, value)

    /**
     * 조사용 포그라운드 서비스 시작을 시도하고 결과(예외 포함)를 남김.
     * 서비스 클래스 이름으로 시작해서 data → service 패키지 의존을 만들지 않음
     */
    fun tryStartService(context: Context, via: String, targets: String = "vehicle") {
        val intent = Intent(PROBE_START)
            .setClassName(context.packageName, "com.example.blinkerleague.service.ProbeService")
            .putExtra("targets", targets)
            .putExtra("via", via)
        runCatching { context.startForegroundService(intent) }
            .onSuccess { event("startForegroundService", "via=$via result=ok") }
            .onFailure { event("startForegroundService", "via=$via failed: $it") }
    }

    /** @param naviPollMs 0보다 크면 NaviHelper getCurrentLocationInfo()를 이 주기로 폴링 (navi 대상일 때) */
    fun start(targets: Set<String>, via: String, naviPollMs: Long = 0L) {
        if ("location" in targets) location.start(via)
        if ("vehicle" in targets) vehicle.start(via)
        if ("odometer" in targets) odometer.start(via)
        if ("speed" in targets) carProperty.start(via)
        if ("navi" in targets) {
            navi.start(via)
            if (naviPollMs > 0) navi.pollCurrentLocation(naviPollMs)
        }
    }

    companion object {
        const val PROBE_START = "com.example.blinkerleague.PROBE_START"
        val ALL = setOf("vehicle", "odometer", "speed", "navi", "location")

        fun parseTargets(raw: String?): Set<String> =
            raw?.split(",")?.map { it.trim() }?.filter { it in ALL }?.toSet()?.ifEmpty { ALL } ?: ALL
    }
}
