package com.example.blinkerleague.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.blinkerleague.AppContainer
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.data.sync.TripUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 운행 수집 포그라운드 서비스 (plan.md 2단계, docs/screen-plan.md A9).
 *
 * 실제 차량·위치 공급원으로 [TripRecorder]를 돌려 운행을 구분·판정·저장함. 앱이 화면에 없어도 신호를 받으려면
 * 포그라운드 서비스여야 함 — 일반 백그라운드는 약 10초 뒤 동결되어 신호를 전부 놓침 (docs/spikes/vehicle-background-speed.md).
 *
 * - type `specialUse`: 차량 신호 수집에 맞는 표준 type이 없음 (Android 14, 실험 6)
 * - 시작 경로: 앱 화면이 보일 때([MainActivity][com.example.blinkerleague.MainActivity]), 부팅·앱 업데이트([BootReceiver]).
 *   동결된 백그라운드에서는 시작할 수 없음. 시동 Resume 때 깨어나는 경로는 미확인
 * - 운행 상태를 음성 안내([TripVoiceGuide])로 넘기고, 운행이 끝나면 업로드 작업([TripUploadWorker])을 예약함
 * - 알림은 "깜빡이리그 · 기록 중" 한 줄. 점수·결과를 알림으로 보여주지 않음 (주행 안전 원칙)
 */
class TripCollectionService : Service() {

    private val container: AppContainer by lazy { (application as BlinkerLeagueApplication).container }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recording: Job? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.collection_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.collection_notification))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        // 서비스 종류 인자는 API 29부터. 하위 버전 호환은 ServiceCompat이 처리 (minSdk 28)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (recording == null) recording = scope.launch { record() }
        return START_STICKY
    }

    private suspend fun record() {
        val recorder = TripRecorder(
            vehicle = container.vehicleSignalSource,
            location = container.locationSource,
            repository = container.tripRepository,
            config = container.config,
            clock = System::currentTimeMillis,
            newId = container::newId,
        )
        Log.i(TAG, "수집 시작, 지원 신호: ${container.vehicleSignalSource.capabilities()}")
        scope.launch {
            recorder.state.collect { state ->
                container.setRecordingStatus(state.toStatus())
                container.voiceGuide.onRecorderState(state)
                when (state) {
                    is RecorderState.Recording -> Log.i(TAG, "운행 시작 ${state.tripId}")
                    is RecorderState.Finished -> {
                        val result = container.tripRepository.observeResult(state.tripId).first()
                        Log.i(
                            TAG,
                            "운행 끝 ${state.tripId} → 저장됨: 거리=%.2fkm 회전=${result?.turns?.map { "${it.direction}:${if (it.signalOn) "켬" else "안 켬"}" }}"
                                .format(result?.distanceKm ?: 0.0),
                        )
                        // 업로드는 기록과 수명이 달라서 WorkManager에 맡김 (네트워크가 연결되면 실행, 실패하면 재시도)
                        TripUploadWorker.enqueue(this@TripCollectionService, reason = "trip-finished")
                    }
                    RecorderState.Idle -> Unit
                }
            }
        }
        recorder.run()
    }

    override fun onDestroy() {
        scope.cancel()
        container.setRecordingStatus(RecordingStatus.OFF)
        Log.i(TAG, "수집 종료")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TripCollection"
        private const val CHANNEL_ID = "trip_collection"
        private const val NOTIFICATION_ID = 1

        /**
         * 서비스 시작. 앱이 보일 때나 부팅·업데이트 브로드캐스트에서 불러야 함 (그 밖의 백그라운드에서는 시스템이 거부함).
         * 실패해도 앱이 죽지 않게 결과만 남김
         */
        fun start(context: Context, reason: String) {
            runCatching { context.startForegroundService(Intent(context, TripCollectionService::class.java)) }
                .onSuccess { Log.i(TAG, "시작 요청 ($reason)") }
                .onFailure { Log.w(TAG, "시작 실패 ($reason): $it") }
        }
    }
}
