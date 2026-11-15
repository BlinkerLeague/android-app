package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.RainConfig
import com.example.blinkerleague.domain.model.HeadlightSample
import com.example.blinkerleague.domain.model.WiperSample
import org.junit.Assert.assertEquals
import org.junit.Test

class RainSegmentDetectorTest {

    private val detector = RainSegmentDetector(RainConfig(minWiperSec = 30))

    @Test
    fun `와이퍼가 minWiperSec 넘게 켜진 구간에서 하향등 꺼진 시간을 잰다`() {
        // given
        // 운행 0s ~ 300s. 와이퍼 60s 켜짐 → 180s 꺼짐 (120초, 기준 30초 초과)
        // 하향등 0s 꺼짐 → 90s 켜짐 → 150s 꺼짐. 와이퍼 구간 중 꺼진 시간은 60~90s, 150~180s로 60초
        val wipers = listOf(
            WiperSample(60_000, on = true),
            WiperSample(180_000, on = false),
        )
        val headlights = listOf(
            HeadlightSample(0, on = false),
            HeadlightSample(90_000, on = true),
            HeadlightSample(150_000, on = false),
        )

        // when
        val result = detector.detect(
            wipers = wipers,
            headlights = headlights,
            tripStartMs = 0,
            tripEndMs = 300_000,
        )

        // then
        assertEquals(
            listOf(
                RainSegment(
                    occurredAtMs = 60_000,
                    durationSec = 120,
                    headlightOffSec = 60,
                    knownLimitSec = 0,
                    overRainLimitSec = 0,
                ),
            ),
            result,
        )
    }
}
