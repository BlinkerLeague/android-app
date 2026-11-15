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

    @Test
    fun `minWiperSec 이하로 잠깐 켠 와이퍼는 우천 구간이 아니다`() {
        // given
        // 운행 0s ~ 300s. 와이퍼 60s 켜짐 → 80s 꺼짐 (20초, 기준 30초 이하)
        val wipers = listOf(
            WiperSample(60_000, on = true),
            WiperSample(80_000, on = false),
        )
        val headlights = listOf(HeadlightSample(0, on = false))

        // when
        val result = detector.detect(
            wipers = wipers,
            headlights = headlights,
            tripStartMs = 0,
            tripEndMs = 300_000,
        )

        // then
        assertEquals(emptyList<RainSegment>(), result)
    }

    @Test
    fun `운행 끝까지 켜져 있던 와이퍼는 운행 끝에서 구간을 닫는다`() {
        // given
        // 운행 0s ~ 300s. 와이퍼 200s 켜진 채 운행 종료 (100초)
        // 하향등 0s 꺼짐 → 250s 켜짐. 와이퍼 구간 중 꺼진 시간은 200~250s로 50초
        val wipers = listOf(WiperSample(200_000, on = true))
        val headlights = listOf(
            HeadlightSample(0, on = false),
            HeadlightSample(250_000, on = true),
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
                    occurredAtMs = 200_000,
                    durationSec = 100,
                    headlightOffSec = 50,
                    knownLimitSec = 0,
                    overRainLimitSec = 0,
                ),
            ),
            result,
        )
    }
}
