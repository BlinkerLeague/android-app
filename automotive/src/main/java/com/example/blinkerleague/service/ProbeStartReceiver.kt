package com.example.blinkerleague.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.example.blinkerleague.BlinkerLeagueApplication

/**
 * 조사용(디버그 빌드만): 브로드캐스트에서 포그라운드 서비스를 시작할 수 있는지 확인함.
 * 부팅·앱 교체(BOOT_COMPLETED, MY_PACKAGE_REPLACED) 경로는 이제 실제 [BootReceiver]가 운행 수집 서비스로 씀 —
 * 디버그 빌드에서 조사용 서비스까지 같이 뜨지 않게 이 수신기에서는 뺐음 (조사 결과는 docs/spikes/vehicle-background-speed.md 실험 6).
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
