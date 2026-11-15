package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.HarshConfig
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
}
