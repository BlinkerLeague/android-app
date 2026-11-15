package com.example.blinkerleague.domain.detection

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * 날짜와 좌표(등록 지역의 대표 좌표)로 일출 · 일몰 시각을 구함. 순수 함수.
 * 시간대는 [zone]으로 주입받음.
 *
 * NOAA 근사식(태양 적위 · 균시차, 천정각 90.833° = 대기굴절 · 태양 반지름 보정)을 씀. 오차는 수 분 이내.
 */
object SunTimes {

    /** 일출 · 일몰 시각. 해가 뜨지 않거나 지지 않는 날(극야 · 백야)은 [of]가 null을 돌려줌 */
    data class Result(
        val sunrise: ZonedDateTime,
        val sunset: ZonedDateTime,
    )

    /** 일출 · 일몰 때 태양 중심의 천정각 */
    private const val ZENITH_DEG = 90.833

    /** @param longitude 동경이 양수 */
    fun of(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): Result? {
        // 그날 정오 기준 연중 각도 (라디안)
        val gamma = 2 * PI / date.lengthOfYear() * (date.dayOfYear - 1)
        val eqTimeMin = 229.18 * (
            0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
            )
        val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        val lat = Math.toRadians(latitude)
        val cosHourAngle = cos(Math.toRadians(ZENITH_DEG)) / (cos(lat) * cos(declination)) - tan(lat) * tan(declination)
        if (cosHourAngle !in -1.0..1.0) return null
        val hourAngleDeg = Math.toDegrees(acos(cosHourAngle))

        // UTC 자정부터의 분. 음수 · 1440 이상이면 앞뒤 날짜로 넘어감
        val sunriseUtcMin = 720 - 4 * (longitude + hourAngleDeg) - eqTimeMin
        val sunsetUtcMin = 720 - 4 * (longitude - hourAngleDeg) - eqTimeMin
        val utcMidnight = date.atStartOfDay(ZoneOffset.UTC)
        fun at(minutes: Double) = utcMidnight.plusSeconds((minutes * 60).roundToLong()).withZoneSameInstant(zone)
        return Result(sunrise = at(sunriseUtcMin), sunset = at(sunsetUtcMin))
    }
}
