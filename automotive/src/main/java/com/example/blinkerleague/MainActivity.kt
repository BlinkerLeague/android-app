package com.example.blinkerleague

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.blinkerleague.service.TripCollectionService
import com.example.blinkerleague.ui.navigation.BlinkerNavHost
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱 화면이 보일 때가 포그라운드 서비스를 시작할 수 있는 확실한 시점 (이미 돌고 있으면 그대로 둠)
        TripCollectionService.start(this, reason = "activity")
        // 조사용(디버그 빌드만): 실행 인텐트의 조사 엑스트라 처리. 릴리스에서는 아무것도 안 함
        DebugTools.onActivityCreated(this)
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

    override fun onResume() {
        super.onResume()
        // 권한은 앱 밖(설정 등)에서도 바뀜. 돌아왔을 때 다시 읽어 화면·신호 공급원에 반영
        (application as BlinkerLeagueApplication).container.permissions.refresh()
    }
}
