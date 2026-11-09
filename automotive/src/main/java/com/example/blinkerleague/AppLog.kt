package com.example.blinkerleague

import android.util.Log

/**
 * 앱 로그. Pleos 앱 보안 가이드 "로그 관리": 출시 빌드에서는 정보 로그(i)를 남기지 않고 경고·오류만 남김.
 * 로그에는 토큰·좌표 등 민감정보를 넣지 않음 (토큰은 어디서도 로그로 찍지 않음)
 */
object AppLog {
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (error != null) Log.w(tag, message, error) else Log.w(tag, message)
    }
}
