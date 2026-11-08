package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.SpeedSample
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 운행 거리 계산. 서버에는 좌표 대신 이 거리(`distanceKm`)만 보냄.
 * 기본은 차량 속도 적분([kmFromSpeeds]). 속도가 없을 때만 위치로 계산함([km]).
 * (트립 거리 API는 에뮬레이터 미지원, 오도미터는 속도를 따라 늘지 않아 검증 불가 — docs/spikes/vehicle-background-speed.md)
 */
object TripDistance {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun km(samples: List<LocationSample>): Double =
        samples.sortedBy { it.timestampMs }
            .zipWithNext { a, b -> haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) }
            .sum() / 1000.0

    /**
     * 속도 샘플을 [startMs, endMs] 구간에서 적분한 거리(km). 샘플 사이는 앞 샘플 값이 유지된다고 봄(직사각형 적분).
     * 후진(음수)도 이동 거리이므로 절댓값을 씀.
     */
    fun kmFromSpeeds(speeds: List<SpeedSample>, startMs: Long, endMs: Long): Double {
        val sorted = speeds.sortedBy { it.timestampMs }
        var meters = 0.0
        for ((i, s) in sorted.withIndex()) {
            val from = maxOf(s.timestampMs, startMs)
            val to = minOf(sorted.getOrNull(i + 1)?.timestampMs ?: endMs, endMs)
            if (to > from) meters += abs(s.speedKmh) / 3.6 * (to - from) / 1000.0
        }
        return meters / 1000.0
    }

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }
}
