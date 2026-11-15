package com.example.blinkerleague.data.navi

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import com.example.blinkerleague.data.debug.ProbeLog

/**
 * 조사용: NaviHelper 이벤트 콜백이 언제·얼마나 자주·무엇을 주는지 Logcat `NaviTest` 태그로 남김.
 *
 * API 이름은 Pleos 문서(NaviHelper > Event listeners, Initialization and release)를 따르고,
 * 콜백 인자 타입은 SDK 3.0.5 클래스(javap)로 확인함. `onRouteEnded`는 문서 목록에는 없고 SDK 클래스에만 있음.
 *
 * 로그 형식은 [ProbeLog] (출처 Navi). `scripts/probe_log_summary.py`로 콜백별 횟수·간격을 요약함.
 */
class NaviEventLogger(context: Context, private val probeLog: ProbeLog) {

    private val naviHelper = NaviHelper(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var pollIntervalMs = 0L

    fun start(via: String = "") {
        if (started) return
        started = true
        log("start", "via=$via")
        log("initialize", "요청")
        naviHelper.initialize(
            onConnected = {
                log("initialize", "onConnected")
                naviHelper.addListener(listener)
            },
            onFailed = { throwable -> log("initialize", "onFailed $throwable") },
        )
    }

    /** 목적지 없이 위치를 받기 위한 폴링 (문서: get API 결과는 대응 콜백 onCurrentLocationInfo로 옴). 0이면 중지 */
    fun pollCurrentLocation(intervalMs: Long) {
        pollIntervalMs = intervalMs
        handler.removeCallbacks(poll)
        log("pollCurrentLocation", "intervalMs=$intervalMs")
        if (intervalMs > 0) handler.post(poll)
    }

    private val poll = object : Runnable {
        override fun run() {
            log("getCurrentLocationInfo", "호출")
            naviHelper.getCurrentLocationInfo()
            if (pollIntervalMs > 0) handler.postDelayed(this, pollIntervalMs)
        }
    }

    fun stop() {
        handler.removeCallbacks(poll)
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

    private fun log(name: String, body: String) = probeLog.log("Navi", name, body)

    private fun dump(value: Any?): String = ProbeLog.dump(value)
}
