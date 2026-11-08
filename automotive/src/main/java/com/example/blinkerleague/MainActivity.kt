package com.example.blinkerleague

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.blinkerleague.data.debug.DebugProbes
import com.example.blinkerleague.ui.navigation.BlinkerNavHost
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 조사용(디버그): adb shell am start -n …/.MainActivity --es probe vehicle,speed
        // 서비스 없이 앱 프로세스에서 프로브를 등록함 (포그라운드/일반 백그라운드 실험)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val probes = (application as BlinkerLeagueApplication).container.debugProbes
        val probe = intent.getStringExtra("probe")
        if (probe != null && debuggable) {
            probes.start(DebugProbes.parseTargets(probe), via = "activity", naviPollMs = intent.getLongExtra("naviPollMs", 0L))
            // 근접 알림: --ed proxLat … --ed proxLng … --ef proxRadius 100
            if (intent.hasExtra("proxLat")) {
                probes.location.addProximityAlert(
                    intent.getDoubleExtra("proxLat", Double.NaN),
                    intent.getDoubleExtra("proxLng", Double.NaN),
                    intent.getFloatExtra("proxRadius", 100f),
                )
            }
        }
        // 조사용: --el fgsDelayMs 5000 → 앱이 백그라운드로 간 뒤 앱 스스로 포그라운드 서비스 시작을 시도
        val fgsDelayMs = intent.getLongExtra("fgsDelayMs", -1L)
        if (fgsDelayMs >= 0 && debuggable) {
            intent.removeExtra("fgsDelayMs") // 작업 복귀 시 같은 인텐트로 다시 실행되지 않게
            probes.event("fgsDelay", "scheduled ${fgsDelayMs}ms")
            Handler(Looper.getMainLooper()).postDelayed(
                { probes.tryStartService(applicationContext, via = "app-delayed-${fgsDelayMs}ms") },
                fgsDelayMs,
            )
        }
        setContent {
            BlinkerLeagueTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BlinkerNavHost()
                }
            }
        }
    }
}
