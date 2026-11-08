package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.model.SpeedSample
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDistanceTest {

    @Test
    fun `속도 적분 - 샘플 사이는 앞 값 유지, 구간 밖은 잘라냄`() {
        // 0~60초 60km/h(1km/분), 60~90초 120km/h
        val speeds = listOf(SpeedSample(0, 60f), SpeedSample(60_000, 120f), SpeedSample(90_000, 0f))
        assertEquals(2.0, TripDistance.kmFromSpeeds(speeds, 0, 120_000), 1e-9)
        assertEquals(0.5, TripDistance.kmFromSpeeds(speeds, 30_000, 60_000), 1e-9)
    }

    @Test
    fun `마지막 샘플은 구간 끝까지 유지되고, 후진도 거리에 더함`() {
        val speeds = listOf(SpeedSample(0, -36f)) // 후진 10m/s
        assertEquals(0.1, TripDistance.kmFromSpeeds(speeds, 0, 10_000), 1e-9)
    }

    @Test
    fun `구간 시작 전 샘플의 값이 구간 처음부터 적용됨`() {
        val speeds = listOf(SpeedSample(0, 36f)) // 10m/s
        assertEquals(0.05, TripDistance.kmFromSpeeds(speeds, 5_000, 10_000), 1e-9)
    }

    @Test
    fun `순서가 섞여도 시각순으로 계산, 샘플이 없으면 0`() {
        val speeds = listOf(SpeedSample(10_000, 0f), SpeedSample(0, 36f))
        assertEquals(0.1, TripDistance.kmFromSpeeds(speeds, 0, 20_000), 1e-9)
        assertEquals(0.0, TripDistance.kmFromSpeeds(emptyList(), 0, 20_000), 0.0)
    }
}
