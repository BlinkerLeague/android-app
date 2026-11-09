package com.example.blinkerleague.data.auth

import android.annotation.SuppressLint
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

/**
 * 앱 전용 SharedPreferences(MODE_PRIVATE)에 저장. 토큰은 [cipher](Android Keystore 키)로 암호화해 둠
 * (서버 plan.md 7장 "토큰은 Keystore 등 안전한 저장소에 보관").
 * - 예전에 평문으로 저장된 토큰은 처음 읽을 때 암호화해 다시 저장
 * - 복호화가 안 되면(키 분실·값 손상) 토큰을 지우고 등록을 풀어 재등록하게 함
 * - 토큰은 저장이 끝난 뒤 다음 요청·화면 이동이 일어나야 하므로 apply가 아니라 commit을 씀
 *
 * @param fileName 서버 모드와 Fake 모드는 다른 파일을 씀 (Fake 토큰을 실제 서버에 보내지 않게)
 */
@SuppressLint("ApplySharedPref")
class PrefsTokenStore(
    context: Context,
    fileName: String = "auth",
    private val cipher: TokenCipher = KeystoreTokenCipher(),
) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val _registered = MutableStateFlow(false)

    init {
        migratePlainTokens()
        _registered.value = refreshToken != null
    }

    override val accessToken: String? get() = read(KEY_ACCESS)
    override val refreshToken: String? get() = read(KEY_REFRESH)
    override val registered: StateFlow<Boolean> = _registered.asStateFlow()

    private fun read(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return cipher.decrypt(stored) ?: run {
            // 키가 없어졌거나 값이 깨짐 → 토큰을 쓸 수 없으므로 재등록
            clear()
            null
        }
    }

    /** 암호화 전 버전이 남긴 평문 토큰을 암호화해 다시 저장 */
    private fun migratePlainTokens() {
        val edit = prefs.edit()
        var changed = false
        for (key in listOf(KEY_ACCESS, KEY_REFRESH)) {
            val value = prefs.getString(key, null) ?: continue
            if (!value.startsWith(TokenCipher.PREFIX)) {
                edit.putString(key, cipher.encrypt(value))
                changed = true
            }
        }
        if (changed) edit.commit()
    }

    override fun saveRegistration(response: RegisterDriverResponse) {
        prefs.edit()
            .putLong("driverId", response.driverId)
            .putString("nickname", response.nickname)
            .putString("regionCode", response.regionCode)
            .putString(KEY_ACCESS, cipher.encrypt(response.accessToken))
            .putString(KEY_REFRESH, cipher.encrypt(response.refreshToken))
            .commit()
        _registered.value = true
    }

    override fun updateTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS, cipher.encrypt(accessToken))
            .putString(KEY_REFRESH, cipher.encrypt(refreshToken))
            .commit()
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
