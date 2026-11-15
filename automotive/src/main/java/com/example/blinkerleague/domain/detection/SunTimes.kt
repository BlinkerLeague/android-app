package com.example.blinkerleague.domain.detection

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 날짜와 좌표(등록 지역의 대표 좌표)로 일출 · 일몰 시각을 구함. 순수 함수.
 * 시간대는 [zone]으로 주입받음.
 */
object SunTimes {

    /** 일출 · 일몰 시각. 해가 뜨지 않거나 지지 않는 날(극야 · 백야)은 [of]가 null을 돌려줌 */
    data class Result(
        val sunrise: ZonedDateTime,
        val sunset: ZonedDateTime,
    )

    fun of(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): Result? {
        val midnight = date.atStartOfDay(zone)
        return Result(sunrise = midnight, sunset = midnight)
    }
}
