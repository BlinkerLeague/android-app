package com.example.blinkerleague.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.data.debug.DebugProbes

/**
 * 조사용 포그라운드 서비스 (디버그 전용). 앱이 백그라운드여도 프로브 콜백을 계속 받기 위함.
 * 자동으로 시작하지 않음 — 일반 백그라운드 실험을 오염시키지 않도록 adb로만 시작함.
 *
 * ```
 * # 프로브 시작 (targets: vehicle,speed,navi 중 쉼표로)
 * adb shell am start-foreground-service -n com.example.blinkerleague/.service.ProbeService \
 *   -a com.example.blinkerleague.PROBE_START --es targets vehicle,speed
 * # 내비: 목적지 설정·취소, 현재 위치 폴링(ms, 0이면 중지)
 *   -a com.example.blinkerleague.NAVI_REQUEST_ROUTE --ed lat … --ed lng … --es name …
 *   -a com.example.blinkerleague.NAVI_CANCEL_ROUTE
 *   -a com.example.blinkerleague.NAVI_POLL_LOCATION --el intervalMs 5000
 * # 위치: 근접 알림 (원형, 서비스에서 location 프로브가 시작돼 있어야 함)
 *   -a com.example.blinkerleague.LOCATION_PROXIMITY --ed lat … --ed lng … --ef radius 100
 * ```
 */
class ProbeService : Service() {

    private val probes: DebugProbes by lazy { (application as BlinkerLeagueApplication).container.debugProbes }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.probe_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.probe_notification))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        runCatching { startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
            .onSuccess { probes.event("startForeground", "ok") }
            .onFailure { probes.event("startForeground", "failed: $it") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        probes.event("ProbeService.onStartCommand", "action=${intent?.action} via=${intent?.getStringExtra("via")}")
        when (intent?.action) {
            ACTION_PROBE_START -> probes.start(
                DebugProbes.parseTargets(intent.getStringExtra("targets")),
                via = "service(${intent.getStringExtra("via") ?: "adb"})",
                naviPollMs = intent.getLongExtra("naviPollMs", 0L),
            )
            ACTION_PROXIMITY -> probes.location.addProximityAlert(
                latitude = intent.getDoubleExtra("lat", Double.NaN),
                longitude = intent.getDoubleExtra("lng", Double.NaN),
                radiusMeters = intent.getFloatExtra("radius", 100f),
            )
            ACTION_REQUEST_ROUTE -> {
                probes.start(setOf("navi"), via = "service")
                probes.navi.requestRoute(
                    latitude = intent.getDoubleExtra("lat", Double.NaN),
                    longitude = intent.getDoubleExtra("lng", Double.NaN),
                    poiName = intent.getStringExtra("name") ?: "test",
                    address = intent.getStringExtra("address") ?: "",
                )
            }
            ACTION_CANCEL_ROUTE -> {
                probes.start(setOf("navi"), via = "service")
                probes.navi.cancelRoute()
            }
            ACTION_POLL_LOCATION -> {
                probes.start(setOf("navi"), via = "service")
                probes.navi.pollCurrentLocation(intent.getLongExtra("intervalMs", 5_000L))
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PROBE_START = DebugProbes.PROBE_START
        const val ACTION_REQUEST_ROUTE = "com.example.blinkerleague.NAVI_REQUEST_ROUTE"
        const val ACTION_CANCEL_ROUTE = "com.example.blinkerleague.NAVI_CANCEL_ROUTE"
        const val ACTION_POLL_LOCATION = "com.example.blinkerleague.NAVI_POLL_LOCATION"
        const val ACTION_PROXIMITY = "com.example.blinkerleague.LOCATION_PROXIMITY"
        private const val CHANNEL_ID = "probe"
        private const val NOTIFICATION_ID = 1001
    }
}
