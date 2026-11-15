package com.example.blinkerleague.data.celebration

import android.content.Context

/**
 * SharedPreferences에 마지막으로 축하한 키 하나만 둠. 퀘스트 회차는 앞으로만 바뀌므로 지난 회차를 쌓아 둘 필요가 없음
 */
class PrefsCelebrationStore(context: Context) : CelebrationStore {
    private val prefs = context.applicationContext.getSharedPreferences("celebration", Context.MODE_PRIVATE)

    override fun isCelebrated(key: String): Boolean = prefs.getString(KEY, null) == key

    override fun markCelebrated(key: String) {
        prefs.edit().putString(KEY, key).apply()
    }

    private companion object {
        const val KEY = "quest_clear"
    }
}
