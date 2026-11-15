package com.example.blinkerleague.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 토큰 문자열 암호화. 저장 형식: `v1:<base64(iv)>:<base64(암호문)>` */
interface TokenCipher {
    fun encrypt(plain: String): String

    /** 복호화 실패(키 분실·값 손상)면 null */
    fun decrypt(stored: String): String?

    companion object {
        const val PREFIX = "v1:"
    }
}

/**
 * Android Keystore의 AES-256 키(기기 밖으로 꺼낼 수 없음)로 AES/GCM 암호화.
 * 서버 plan.md 7장 "토큰은 Keystore 등 안전한 저장소에 보관". 키가 없어지면(앱 데이터 복원 등) 복호화가 실패해 재등록함
 */
class KeystoreTokenCipher(private val alias: String = "blinkerleague_token_key") : TokenCipher {

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return TokenCipher.PREFIX + b64(cipher.iv) + ":" + b64(encrypted)
    }

    override fun decrypt(stored: String): String? = runCatching {
        val parts = stored.removePrefix(TokenCipher.PREFIX).split(":")
        require(stored.startsWith(TokenCipher.PREFIX) && parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, unb64(parts[0])))
        }
        String(cipher.doFinal(unb64(parts[1])), Charsets.UTF_8)
    }.getOrNull()

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
