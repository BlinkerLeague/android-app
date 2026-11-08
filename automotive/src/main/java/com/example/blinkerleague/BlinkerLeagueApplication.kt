package com.example.blinkerleague

import android.app.Application
import com.example.blinkerleague.data.sync.TripUploadWorker
import kotlinx.coroutines.launch

class BlinkerLeagueApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 지난번에 올리지 못한 운행이 있으면 네트워크가 연결될 때 올림
        TripUploadWorker.enqueue(this, reason = "app-start")
        // 기준값은 version이 바뀐 경우에만 갱신. 실패하면 저장 값·기본값 그대로
        container.applicationScope.launch { container.configRepository.refresh() }
    }
}
