package com.example.blinkerleague.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 부팅·앱 업데이트 직후 운행 수집 서비스를 시작함.
 * Android 문서상 BOOT_COMPLETED·MY_PACKAGE_REPLACED 수신은 백그라운드 포그라운드 서비스 시작 제한의 예외이고,
 * 에뮬레이터에서 실제로 허용됨을 확인함 (docs/spikes/vehicle-background-speed.md 실험 6).
 * 사용자가 강제 종료한 앱은 다시 열기 전까지 이 브로드캐스트를 받지 못함.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                TripCollectionService.start(context, reason = intent.action!!.substringAfterLast('.'))
        }
    }
}
