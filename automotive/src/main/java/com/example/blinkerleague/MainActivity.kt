package com.example.blinkerleague

import android.content.pm.ApplicationInfo
import android.os.Bundle
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
        val probe = intent.getStringExtra("probe")
        if (probe != null && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            (application as BlinkerLeagueApplication).container.debugProbes.start(DebugProbes.parseTargets(probe), via = "activity")
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
