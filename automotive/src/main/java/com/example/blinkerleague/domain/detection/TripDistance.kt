package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.model.LocationSample
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 위치 시계열로 운행 거리를 계산함. 서버에는 좌표 대신 이 거리(`distanceKm`)만 보냄. */
object TripDistance {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun km(samples: List<LocationSample>): Double =
        samples.sortedBy { it.timestampMs }
            .zipWithNext { a, b -> haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) }
            .sum() / 1000.0

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }
}
