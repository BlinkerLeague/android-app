package com.example.blinkerleague

import android.app.Application
import android.content.pm.ApplicationInfo
import com.example.blinkerleague.service.NaviLogService

class BlinkerLeagueApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // 조사용 내비 이벤트 로그. 백그라운드에서도 받도록 포그라운드 서비스에서 돌림
            NaviLogService.start(this)
        }
    }
}
