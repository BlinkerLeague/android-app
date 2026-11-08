package com.example.blinkerleague.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R

/**
 * 조사용 포그라운드 서비스: 앱이 백그라운드(내비 앱이 앞에 있을 때)여도 NaviHelper 이벤트를 계속 받아 로그로 남김.
 * 백그라운드에서는 앱 프로세스가 cached 상태로 얼어 콜백을 못 받기 때문.
 * 디버그 빌드에서만 시작함 ([BlinkerLeagueApplication]).
 */
class NaviLogService : Service() {

    private val logger by lazy { (application as BlinkerLeagueApplication).container.naviEventLogger }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.navi_log_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.navi_log_notification))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        logger.start()
    }

    /**
     * adb로 목적지 설정·취소를 요청함 (비교 실험용):
     * `adb shell am start-foreground-service -n com.example.blinkerleague/.service.NaviLogService -a ACTION --ed lat … --ed lng … --es name …`
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REQUEST_ROUTE -> logger.requestRoute(
                latitude = intent.getDoubleExtra("lat", Double.NaN),
                longitude = intent.getDoubleExtra("lng", Double.NaN),
                poiName = intent.getStringExtra("name") ?: "test",
                address = intent.getStringExtra("address") ?: "",
            )
            ACTION_CANCEL_ROUTE -> logger.cancelRoute()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        logger.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_REQUEST_ROUTE = "com.example.blinkerleague.NAVI_REQUEST_ROUTE"
        const val ACTION_CANCEL_ROUTE = "com.example.blinkerleague.NAVI_CANCEL_ROUTE"
        private const val CHANNEL_ID = "navi_log"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NaviLogService::class.java))
        }
    }
}
