package com.example.blinkerleague.data.remote.http

import com.example.blinkerleague.data.auth.TokenStore
import com.example.blinkerleague.data.remote.dto.RefreshRequest
import com.example.blinkerleague.data.remote.dto.TokenResponse
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * 서버 연결 구성 (Retrofit + OkHttp + kotlinx.serialization).
 *
 * - 모든 요청에 `Authorization: Bearer <access>` (토큰이 있을 때)
 * - 401이면 `POST /api/auth/refresh`로 새 토큰을 받아 한 번 다시 보냄 (Access 1시간, 서버 plan.md 8장)
 * - Refresh도 거부되면 토큰을 지움 → 재등록 필요 (`TokenStore.registered` = false)
 */
object HttpClientFactory {
    private val JSON_TYPE = "application/json".toMediaType()

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // 기본값이 있는 필드에 서버가 null을 보내면 기본값으로 (응답 전체를 버리지 않게)
        coerceInputValues = true
    }

    fun create(baseUrl: String, tokens: TokenStore): BlinkerService {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        // Pleos 앱 보안 가이드: TLS 1.2 이상만(MODERN_TLS = TLS 1.3·1.2). http는 디버그 빌드의 로컬 서버(10.0.2.2)뿐이고
        // 릴리스는 network security config 기본값이 평문을 막음
        val specs = if (url.startsWith("https://")) listOf(ConnectionSpec.MODERN_TLS) else listOf(ConnectionSpec.CLEARTEXT)
        val plain = OkHttpClient.Builder()
            .connectionSpecs(specs)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val client = plain.newBuilder()
            .addInterceptor(BearerInterceptor(tokens))
            .authenticator(RefreshAuthenticator(tokens) { refreshToken -> refresh(plain, url, refreshToken) })
            .build()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_TYPE))
            .build()
            .create(BlinkerService::class.java)
    }

    internal fun connectionSpecsFor(baseUrl: String): List<ConnectionSpec> = emptyList()

    /** 새 토큰 쌍. 서버가 거부(401 등)하면 null, 연결 실패면 예외(→ 원래 요청도 연결 실패로 끝남) */
    private fun refresh(client: OkHttpClient, baseUrl: String, refreshToken: String): TokenResponse? {
        val request = Request.Builder()
            .url(baseUrl + "api/auth/refresh")
            .post(json.encodeToString(RefreshRequest(refreshToken)).toRequestBody(JSON_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return json.decodeFromString<TokenResponse>(response.body?.string().orEmpty())
        }
    }
}

internal class BearerInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.accessToken
        val request = if (token == null || chain.request().header("Authorization") != null) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }
}

internal class RefreshAuthenticator(
    private val tokens: TokenStore,
    private val refresh: (refreshToken: String) -> TokenResponse?,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // 이미 한 번 다시 보냈음
        synchronized(this) {
            val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
            val current = tokens.accessToken
            val token = if (current != null && current != sent) {
                current // 다른 요청이 먼저 재발급함
            } else {
                val refreshToken = tokens.refreshToken ?: return null
                val renewed = refresh(refreshToken)
                if (renewed == null) {
                    tokens.clear()
                    return null
                }
                tokens.updateTokens(renewed.accessToken, renewed.refreshToken)
                renewed.accessToken
            }
            return response.request.newBuilder().header("Authorization", "Bearer $token").build()
        }
    }
}
