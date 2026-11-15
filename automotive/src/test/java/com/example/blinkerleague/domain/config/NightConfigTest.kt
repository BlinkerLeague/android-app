package com.example.blinkerleague.domain.config

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightConfigTest {

    @Test
    fun `서울 지역 코드는 서울 대표 좌표를 쓰고 목록에 없는 지역 코드는 null`() {
        // given
        // 11140 = 서울 중구 (Regions.kt), 99999 = 목록에 없는 코드
        val config = DetectionConfig.DEFAULT.night

        // when
        val seoul = config.representativeCoordinate("11140")
        val unknown = config.representativeCoordinate("99999")

        // then
        assertNotNull("서울 중구(11140)의 대표 좌표가 없음", seoul)
        assertTrue("위도 ${seoul!!.latitude}가 서울 범위(37.5~37.6) 밖", seoul.latitude in 37.5..37.6)
        assertTrue("경도 ${seoul.longitude}가 서울 범위(126.9~127.1) 밖", seoul.longitude in 126.9..127.1)
        assertNull(unknown)
    }
}
