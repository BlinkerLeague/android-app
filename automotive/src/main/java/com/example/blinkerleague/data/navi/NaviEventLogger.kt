package com.example.blinkerleague.data.navi

import android.content.Context
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import java.lang.reflect.Modifier
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 조사용: NaviHelper 이벤트 콜백이 언제·얼마나 자주·무엇을 주는지 Logcat `NaviTest` 태그로 남김.
 *
 * API 이름은 Pleos 문서(NaviHelper > Event listeners, Initialization and release)를 따르고,
 * 콜백 인자 타입은 SDK 3.0.5 클래스(javap)로 확인함. `onRouteEnded`는 문서 목록에는 없고 SDK 클래스에만 있음.
 *
 * 로그 한 줄 형식:
 * `HH:mm:ss.SSS +경과초 dt=같은콜백간격초 #횟수 콜백이름 {필드 전체}`
 * `scripts/navi_log_summary.py`로 콜백별 횟수·간격을 요약함.
 */
class NaviEventLogger(context: Context) {

    private val naviHelper = NaviHelper(context.applicationContext)
    private val startedAt = SystemClock.elapsedRealtime()
    private val lastAt = mutableMapOf<String, Long>()
    private val counts = mutableMapOf<String, Int>()
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun start() {
        log("initialize", "요청")
        naviHelper.initialize(
            onConnected = {
                log("initialize", "onConnected")
                naviHelper.addListener(listener)
            },
            onFailed = { throwable -> log("initialize", "onFailed $throwable") },
        )
    }

    fun stop() {
        naviHelper.removeListener(listener)
        naviHelper.release()
        log("release", "")
    }

    /** 비교 실험용 목적지 설정 (문서 NaviHelper > Destinations의 requestRoute 예시와 같은 형식) */
    fun requestRoute(latitude: Double, longitude: Double, poiName: String, address: String) {
        log("requestRoute", "lat=$latitude lng=$longitude name=$poiName")
        naviHelper.requestRoute(
            RouteInfo(
                latitude = latitude,
                longitude = longitude,
                displayLongitude = null,
                displayLatitude = null,
                poiName = poiName,
                poiId = "",
                poiSubId = "0",
                address = address,
            ),
        )
    }

    fun cancelRoute() {
        log("cancelRoute", "")
        naviHelper.cancelRoute()
    }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = log("onRouteStarted", dump(routeStartInfo))
        override fun onRouteEnded() = log("onRouteEnded", "")
        override fun onRoutingCompleted() = log("onRoutingCompleted", "")
        override fun onReRouteEnded() = log("onReRouteEnded", "")
        override fun onRouteOptionChanged(routeOption: RouteOption) = log("onRouteOptionChanged", dump(routeOption))
        override fun onRouteCancelled() = log("onRouteCancelled", "")
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) =
            log("onWaypointChanged", dump(waypointChangedInfo))
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
            log("onDestinationArrived", dump(destinationArrivedInfo))
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) =
            log("onWayPointArrived", dump(waypointArrivedInfo))
        override fun onBookMarkChanged() = log("onBookMarkChanged", "")
        override fun onDestinationChanged() = log("onDestinationChanged", "")
        override fun onError(errorCode: NaviErrorCode) = log("onError", dump(errorCode))
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) =
            log("onCurrentLocationInfo", dump(currentLocationInfo))
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = log("onRouteStateInfo", dump(routeStateInfo))
        override fun onDestinationInfo(destinationInfo: DestinationInfo) =
            log("onDestinationInfo", dump(destinationInfo))
        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) = log("onWayPointInfo", dump(waypointInfo))
        override fun onRecentDestinationInfo(recentDestinationInfo: List<RecentDestinationInfo>) =
            log("onRecentDestinationInfo", dump(recentDestinationInfo))
        override fun onBookmarkInfo(bookmarkInfo: List<BookmarkInfo>) = log("onBookmarkInfo", dump(bookmarkInfo))
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = log("onCurrentRoadSpeedLimit", speedLimit.toString())
        override fun onCameraAlert(cameraAlert: List<CameraAlert>) = log("onCameraAlert", dump(cameraAlert))
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = log("onDrivingInfo", dump(drivingInfo))
        override fun onNavigationStatus(navigationStatus: NavigationStatus) =
            log("onNavigationStatus", dump(navigationStatus))
        override fun onTBTInfo(tbtInfo: List<TBTInfo>) = log("onTBTInfo", dump(tbtInfo))
        override fun onChargerOperatorInfo(chargerOperator: List<ChargerOperator>) =
            log("onChargerOperatorInfo", dump(chargerOperator))
        override fun onCautionPointInfo(cautionPoint: List<CautionPoint>) =
            log("onCautionPointInfo", dump(cautionPoint))
        override fun onTripInfo(tripInfo: TripInfo) = log("onTripInfo", dump(tripInfo))
    }

    @Synchronized
    private fun log(name: String, body: String) {
        val now = SystemClock.elapsedRealtime()
        val dt = lastAt[name]?.let { "%.3f".format((now - it) / 1000.0) } ?: "-"
        lastAt[name] = now
        val n = (counts[name] ?: 0) + 1
        counts[name] = n
        val line = "${LocalTime.now().format(clock)} +%.3f dt=$dt #$n $name $body".format((now - startedAt) / 1000.0)
        // Logcat 한 줄 길이 제한(약 4KB) 때문에 나눠서 찍음. 이어지는 줄은 "  ↳"로 시작
        line.chunked(3_500).forEachIndexed { i, part -> Log.i(TAG, if (i == 0) part else "  ↳$part") }
    }

    /**
     * 객체의 필드를 전부 문자열로. SDK 데이터 클래스의 공개 getter(get·is로 시작)를 리플렉션으로 훑고,
     * 중첩된 Pleos 타입·리스트는 재귀로 펼침 (toString이 모든 필드를 보여준다는 보장이 없어서)
     */
    private fun dump(value: Any?, depth: Int = 0): String = when {
        value == null -> "null"
        value is Enum<*> -> value.name
        value is CharSequence -> "\"$value\""
        value is Number || value is Boolean -> value.toString()
        value is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { dump(it, depth + 1) }
        depth > MAX_DEPTH || !value.javaClass.name.startsWith("ai.pleos.") -> value.toString()
        else -> {
            val props = value.javaClass.methods
                .filter { m ->
                    m.parameterCount == 0 && !Modifier.isStatic(m.modifiers) && m.declaringClass != Any::class.java &&
                        (m.name.startsWith("get") && m.name.length > 3 || m.name.startsWith("is") && m.name.length > 2)
                }
                .sortedBy { it.name }
                .joinToString(", ") { m ->
                    val name = m.name.removePrefix("get").removePrefix("is").replaceFirstChar { it.lowercase() }
                    val v = runCatching { dump(m.invoke(value), depth + 1) }.getOrElse { "<${it.javaClass.simpleName}>" }
                    "$name=$v"
                }
            "${value.javaClass.simpleName}{$props}"
        }
    }

    private companion object {
        const val TAG = "NaviTest"
        const val MAX_DEPTH = 5
    }
}
