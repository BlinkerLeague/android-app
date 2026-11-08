package com.example.blinkerleague

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blinkerleague.ui.driving.DrivingGuardViewModel
import com.example.blinkerleague.ui.driving.DrivingScreen
import com.example.blinkerleague.service.TripCollectionService
import com.example.blinkerleague.ui.navigation.BlinkerNavHost
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱 화면이 보일 때가 포그라운드 서비스를 시작할 수 있는 확실한 시점 (이미 돌고 있으면 그대로 둠)
        TripCollectionService.start(this, reason = "activity")
        // 조사용(디버그 빌드만): 실행 인텐트의 조사 엑스트라 처리. 릴리스에서는 아무것도 안 함
        DebugTools.onActivityCreated(this)
        val container = (application as BlinkerLeagueApplication).container
        val guardFactory = viewModelFactory {
            initializer {
                DrivingGuardViewModel(
                    gears = container.vehicleSignalSource.vehicleStates.map { it.gear },
                    speeds = container.vehicleSignalSource.speeds.map { it.speedKmh },
                    recordingStatus = container.recordingStatus,
                    bypass = container.guardBypass,
                    config = container.config.guard,
                )
            }
        }
        setContent {
            BlinkerLeagueTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val guard by viewModel<DrivingGuardViewModel>(factory = guardFactory).uiState.collectAsStateWithLifecycle()
                    // 정차 화면(NavHost)은 주행 중에도 그대로 두어 P로 돌아오면 보던 화면이 남음.
                    // 주행 중에는 기록 중 화면이 위를 덮고(터치 차단), 아래 화면은 접근성 트리에서도 숨김
                    Box(Modifier.fillMaxSize()) {
                        Box(if (guard.showParkedScreens) Modifier.fillMaxSize() else Modifier.fillMaxSize().clearAndSetSemantics { }) {
                            BlinkerNavHost()
                        }
                        if (!guard.showParkedScreens) DrivingScreen(guard)
                    }
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
