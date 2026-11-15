package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.HarshConfig
import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.SpeedSample
import org.junit.Assert.assertEquals
import org.junit.Test

class HarshDrivingDetectorTest {

    private val detector = HarshDrivingDetector(HarshConfig(deltaKmhPerSec = 10, maxGapSec = 2))

    @Test
    fun `1초 사이 속도가 deltaKmhPerSec 이상 늘면 급가속 한 번`() {
        // given
        // 0.5초 간격: 20 → 20 → 20 → 26 → 32 → 33km/h. 2s에서 1초 전(1s) 20km/h보다 12km/h 늘어남
        val speeds = listOf(
            SpeedSample(0, 20f),
            SpeedSample(500, 20f),
            SpeedSample(1_000, 20f),
            SpeedSample(1_500, 26f),
            SpeedSample(2_000, 32f),
            SpeedSample(2_500, 33f),
        )

        // when
        val result = detector.detect(speeds = speeds, brakes = emptyList())

        // then
        assertEquals(listOf(HarshEvent(occurredAtMs = 2_000, kind = HarshKind.ACCEL)), result)
    }

    @Test
    fun `급가속이 이어지는 동안은 한 번만 센다`() {
        // given
        // 0.5초 간격: 20 → 20 → 20 → 30 → 40 → 50 → 55 → 56 → 56km/h
        // 1.5s ~ 3s는 모두 1초 전보다 10km/h 이상 늘어남, 3.5s부터 기준 미만
        val speeds = listOf(
            SpeedSample(0, 20f),
            SpeedSample(500, 20f),
            SpeedSample(1_000, 20f),
            SpeedSample(1_500, 30f),
            SpeedSample(2_000, 40f),
            SpeedSample(2_500, 50f),
            SpeedSample(3_000, 55f),
            SpeedSample(3_500, 56f),
            SpeedSample(4_000, 56f),
        )

        // when
        val result = detector.detect(speeds = speeds, brakes = emptyList())

        // then
        assertEquals(listOf(HarshEvent(occurredAtMs = 1_500, kind = HarshKind.ACCEL)), result)
    }

    @Test
    fun `1초 사이 deltaKmhPerSec 이상 줄고 브레이크가 눌려 있으면 급감속`() {
        // given
        // 0.5초 간격: 50 → 50 → 50 → 44 → 38 → 37km/h. 2s에서 1초 전(1s) 50km/h보다 12km/h 줄어듦
        // 브레이크는 1.2s에 밟고 2.6s에 뗌
        val speeds = listOf(
            SpeedSample(0, 50f),
            SpeedSample(500, 50f),
            SpeedSample(1_000, 50f),
            SpeedSample(1_500, 44f),
            SpeedSample(2_000, 38f),
            SpeedSample(2_500, 37f),
        )
        val brakes = listOf(BrakeSample(1_200, pressed = true), BrakeSample(2_600, pressed = false))

        // when
        val result = detector.detect(speeds = speeds, brakes = brakes)

        // then
        assertEquals(listOf(HarshEvent(occurredAtMs = 2_000, kind = HarshKind.BRAKE)), result)
    }
}
