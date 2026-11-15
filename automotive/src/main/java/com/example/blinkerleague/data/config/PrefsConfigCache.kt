package com.example.blinkerleague.data.config

import android.content.Context

class PrefsConfigCache(context: Context) : ConfigCache {
    private val prefs = context.applicationContext.getSharedPreferences("config", Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY, null)

    override fun save(json: String) {
        prefs.edit().putString(KEY, json).apply()
    }

    private companion object {
        const val KEY = "thresholds"
    }
}
