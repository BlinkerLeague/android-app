package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.SafetyWarningConfig
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.TelltaleSample
import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyWarningDetectorTest {

    private val detector = SafetyWarningDetector(
        SafetyWarningConfig(telltaleTypes = setOf("ABS"), movingMinSpeedKmh = 10),
    )

    @Test
    fun `설정한 경고등이 켜진 채 달리기 시작하면 한 번 센다`() {
        // given
        // 0 ~ 30초 동안 30km/h로 주행, 경고등은 1초 폴링: 0 ~ 9초 꺼짐, 10초부터 ABS 켜짐
        val speeds = (0..30).map { SpeedSample(it * 1_000L, 30f) }
        val telltales = (0..30).map { sec ->
            TelltaleSample(sec * 1_000L, if (sec >= 10) setOf("ABS") else emptySet())
        }

        // when
        val result = detector.detect(
            telltales = telltales,
            speeds = speeds,
            tripStartMs = 0,
            tripEndMs = 30_000,
        )

        // then
        assertEquals(listOf(SafetyWarningHit(occurredAtMs = 10_000, type = "ABS")), result)
    }
}
