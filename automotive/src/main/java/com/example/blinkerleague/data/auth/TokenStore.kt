package com.example.blinkerleague.data.auth

import android.content.Context
import com.example.blinkerleague.data.remote.dto.RegisterDriverResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 등록 정보와 토큰 보관. 토큰을 잃으면 재등록함 (서버 plan.md 3.2, P0 결정) */
interface TokenStore {
    val accessToken: String?
    val refreshToken: String?

    /** 등록돼 있는지 (온보딩/홈 시작 화면 분기, screen-plan 4.1) */
    val registered: StateFlow<Boolean>

    fun saveRegistration(response: RegisterDriverResponse)

    fun updateTokens(accessToken: String, refreshToken: String)

    /** Refresh까지 거부됨 → 재등록 필요 */
    fun clear()
}

/** 앱 전용 SharedPreferences에 저장 (MODE_PRIVATE) */
class PrefsTokenStore(context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val _registered = MutableStateFlow(prefs.getString(KEY_REFRESH, null) != null)

    override val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)
    override val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)
    override val registered: StateFlow<Boolean> = _registered.asStateFlow()

    override fun saveRegistration(response: RegisterDriverResponse) {
        prefs.edit()
            .putLong("driverId", response.driverId)
            .putString("nickname", response.nickname)
            .putString("regionCode", response.regionCode)
            .putString(KEY_ACCESS, response.accessToken)
            .putString(KEY_REFRESH, response.refreshToken)
            .commit()
        _registered.value = true
    }

    override fun updateTokens(accessToken: String, refreshToken: String) {
        prefs.edit().putString(KEY_ACCESS, accessToken).putString(KEY_REFRESH, refreshToken).commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
        _registered.value = false
    }

    private companion object {
        const val KEY_ACCESS = "accessToken"
        const val KEY_REFRESH = "refreshToken"
    }
}

/** 테스트·Fake 모드용 */
class InMemoryTokenStore : TokenStore {
    override var accessToken: String? = null
        private set
    override var refreshToken: String? = null
        private set
    private val _registered = MutableStateFlow(false)
    override val registered: StateFlow<Boolean> = _registered.asStateFlow()

    override fun saveRegistration(response: RegisterDriverResponse) {
        updateTokens(response.accessToken, response.refreshToken)
        _registered.value = true
    }

    override fun updateTokens(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    override fun clear() {
        accessToken = null
        refreshToken = null
        _registered.value = false
    }
}
