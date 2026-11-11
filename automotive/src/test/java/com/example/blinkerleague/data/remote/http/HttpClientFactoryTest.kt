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
}
