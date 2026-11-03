package com.example.blinkerleague

import android.app.Application

class BlinkerLeagueApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
