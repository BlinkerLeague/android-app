package com.example.blinkerleague.domain.detection

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SunTimesTest {

    @Test
    fun `적도 춘분에는 일출이 6시 일몰이 18시 근처다`() {
        // given
        // 위도 0 · 경도 0, 2026-03-20(춘분), UTC. 낮과 밤이 12시간씩이고 남중이 12시 근처라
        // 일출 ≈ 06:00, 일몰 ≈ 18:00. 균시차 · 대기굴절을 감안해 ±15분 허용
        val date = LocalDate.of(2026, 3, 20)
        val tolerance = Duration.ofMinutes(15)

        // when
        val result = SunTimes.of(date, latitude = 0.0, longitude = 0.0, zone = ZoneOffset.UTC)

        // then
        assertNotNull(result)
        val expectedSunrise = ZonedDateTime.of(2026, 3, 20, 6, 0, 0, 0, ZoneOffset.UTC)
        val expectedSunset = ZonedDateTime.of(2026, 3, 20, 18, 0, 0, 0, ZoneOffset.UTC)
        val sunriseDiff = Duration.between(expectedSunrise, result!!.sunrise).abs()
        val sunsetDiff = Duration.between(expectedSunset, result.sunset).abs()
        assertTrue("일출 ${result.sunrise}가 06:00 UTC에서 15분 넘게 벗어남", sunriseDiff <= tolerance)
        assertTrue("일몰 ${result.sunset}가 18:00 UTC에서 15분 넘게 벗어남", sunsetDiff <= tolerance)
    }
}
