package com.example.blinkerleague.data.debug

import android.content.Context
import com.example.blinkerleague.data.navi.NaviEventLogger
import com.example.blinkerleague.data.vehicle.CarPropertyProbe
import com.example.blinkerleague.data.vehicle.VehicleProbe

/**
 * 조사용 프로브 모음 (디버그 빌드에서만 씀). 같은 프로세스에서 한 번만 시작됨.
 * - vehicle: Pleos Vehicle SDK 방향지시등·조향각·기어 → Logcat VehicleTest
 * - speed: android.car CarPropertyManager 속도·기어 등 → Logcat SpeedTest
 * - navi: NaviHelper 이벤트 → Logcat NaviTest
 *
 * 시작 경로(via)로 실험 상태를 구분함: activity(앱 프로세스에서 등록, 서비스 없음) / service(포그라운드 서비스에서 등록)
 */
class DebugProbes(context: Context) {
    private val appContext = context.applicationContext

    val vehicle by lazy { VehicleProbe(appContext, ProbeLog("VehicleTest")) }
    val carProperty by lazy { CarPropertyProbe(appContext, ProbeLog("SpeedTest")) }
    val navi by lazy { NaviEventLogger(appContext, ProbeLog("NaviTest")) }

    fun start(targets: Set<String>, via: String) {
        if ("vehicle" in targets) vehicle.start(via)
        if ("speed" in targets) carProperty.start(via)
        if ("navi" in targets) navi.start(via)
    }

    companion object {
        val ALL = setOf("vehicle", "speed", "navi")

        fun parseTargets(raw: String?): Set<String> =
            raw?.split(",")?.map { it.trim() }?.filter { it in ALL }?.toSet()?.ifEmpty { ALL } ?: ALL
    }
}
