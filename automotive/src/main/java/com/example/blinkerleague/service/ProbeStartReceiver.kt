package com.example.blinkerleague.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.example.blinkerleague.BlinkerLeagueApplication

/**
 * 조사용(디버그 빌드만): 부팅·앱 교체 브로드캐스트에서 포그라운드 서비스를 시작할 수 있는지 확인함.
 * Android 문서상 BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, MY_PACKAGE_REPLACED 수신은 백그라운드 시작 제한의 예외.
 * PROBE_TRIGGER는 예외에 해당하지 않는 일반 브로드캐스트로, 백그라운드(동결된) 앱이 이벤트를 받아 시작을 시도하는 경우를 흉내 냄:
 * `adb shell am broadcast -n com.example.blinkerleague/.service.ProbeStartReceiver -a com.example.blinkerleague.PROBE_TRIGGER`
 * 결과는 Logcat VehicleTest의 [Probe] 출처로 남김.
 */
class ProbeStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val probes = (context.applicationContext as BlinkerLeagueApplication).container.debugProbes
        probes.event("receiver", intent.action ?: "null")
        probes.tryStartService(context, via = "receiver:${intent.action?.substringAfterLast('.')}")
    }
}
