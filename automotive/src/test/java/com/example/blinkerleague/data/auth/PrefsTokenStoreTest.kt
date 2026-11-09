package com.example.blinkerleague.data.auth

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.blinkerleague.data.remote.dto.RegisterDriverResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric에는 AndroidKeyStore가 없어서 뒤집기 암호로 저장 형식·이전·실패 처리만 확인 (실제 Keystore는 에뮬레이터에서 확인) */
@RunWith(RobolectricTestRunner::class)
class PrefsTokenStoreTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = app.getSharedPreferences("auth_test", Context.MODE_PRIVATE)

    private class ReverseCipher(var broken: Boolean = false) : TokenCipher {
        override fun encrypt(plain: String) = TokenCipher.PREFIX + plain.reversed()
        override fun decrypt(stored: String) =
            if (broken || !stored.startsWith(TokenCipher.PREFIX)) null else stored.removePrefix(TokenCipher.PREFIX).reversed()
    }

    private fun store(cipher: TokenCipher = ReverseCipher()) = PrefsTokenStore(app, "auth_test", cipher)

    @Test
    fun `토큰은 암호화해 저장하고 읽을 때 복호화`() {
        val store = store()
        store.saveRegistration(RegisterDriverResponse(1, "깜빡왕", "11680", "access-1", "refresh-1"))

        assertEquals("v1:1-ssecca", prefs.getString("accessToken", null))
        assertEquals("v1:왕빡깜", prefs.getString("nickname", null))
        assertEquals("v1:08611", prefs.getString("regionCode", null))
        assertEquals("v1:1", prefs.getString("driverId", null))
        assertEquals("access-1", store.accessToken)
        assertTrue(store.registered.value)

        store.updateTokens("access-2", "refresh-2")
        assertEquals("v1:2-hserfer", prefs.getString("refreshToken", null))
        assertEquals("refresh-2", store(ReverseCipher()).refreshToken)
    }

    @Test
    fun `예전 평문 토큰은 처음 열 때 암호화해 다시 저장`() {
        prefs.edit().putString("accessToken", "plain-access").putString("refreshToken", "plain-refresh").commit()

        val store = store()
        assertTrue(store.registered.value)
        assertEquals("v1:ssecca-nialp", prefs.getString("accessToken", null))
        assertEquals("plain-refresh", store.refreshToken)
    }

    @Test
    fun `예전 버전의 평문 닉네임·지역·Long 운전자 ID도 암호화`() {
        prefs.edit().putString("refreshToken", "r").putString("nickname", "깜빡왕").putString("regionCode", "11680")
            .putLong("driverId", 7).commit()

        store()
        assertEquals("v1:왕빡깜", prefs.getString("nickname", null))
        assertEquals("v1:7", prefs.getString("driverId", null))
    }

    @Test
    fun `복호화가 안 되면 토큰을 지우고 등록을 풂`() {
        store().saveRegistration(RegisterDriverResponse(1, "깜빡왕", "11680", "access-1", "refresh-1"))

        val broken = store(ReverseCipher(broken = true))
        assertFalse(broken.registered.value)
        assertNull(broken.accessToken)
        assertNull(prefs.getString("refreshToken", null))
    }
}
