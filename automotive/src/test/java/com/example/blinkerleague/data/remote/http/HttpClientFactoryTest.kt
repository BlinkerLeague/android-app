package com.example.blinkerleague.data.remote.http

import okhttp3.ConnectionSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpClientFactoryTest {

    @Test
    fun `https 주소면 TLS 1_2 이상 연결 사양만 쓴다`() {
        // given
        val baseUrl = "https://api.example.com/"

        // when
        val specs = HttpClientFactory.connectionSpecsFor(baseUrl)

        // then
        assertEquals(listOf(ConnectionSpec.MODERN_TLS), specs)
    }

    @Test
    fun `http 주소면 평문 연결 사양만 쓴다`() {
        // given
        // 디버그 빌드의 로컬 서버(에뮬레이터에서 본 호스트)
        val baseUrl = "http://10.0.2.2:8080/"

        // when
        val specs = HttpClientFactory.connectionSpecsFor(baseUrl)

        // then
        assertEquals(listOf(ConnectionSpec.CLEARTEXT), specs)
    }

    @Test
    fun `스킴이 대문자여도 https 주소면 TLS 연결 사양을 쓴다`() {
        // given
        val baseUrl = "HTTPS://api.example.com/"

        // when
        val specs = HttpClientFactory.connectionSpecsFor(baseUrl)

        // then
        assertEquals(listOf(ConnectionSpec.MODERN_TLS), specs)
    }
}
