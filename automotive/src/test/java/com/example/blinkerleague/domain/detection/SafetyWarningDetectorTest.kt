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

    @Test
    fun `설정에 없는 경고등은 세지 않는다`() {
        // given
        // 0 ~ 30초 동안 30km/h로 주행, 10초부터 설정에 없는 TPMS만 켜짐
        val speeds = (0..30).map { SpeedSample(it * 1_000L, 30f) }
        val telltales = (0..30).map { sec ->
            TelltaleSample(sec * 1_000L, if (sec >= 10) setOf("TPMS") else emptySet())
        }

        // when
        val result = detector.detect(
            telltales = telltales,
            speeds = speeds,
            tripStartMs = 0,
            tripEndMs = 30_000,
        )

        // then
        assertEquals(emptyList<SafetyWarningHit>(), result)
    }

    @Test
    fun `계속 켜져 있으면 다시 세지 않고 꺼졌다 켜지면 다시 센다`() {
        // given
        // 0 ~ 40초 동안 30km/h로 주행. ABS는 10 ~ 19초 켜짐, 20 ~ 29초 꺼짐, 30초부터 다시 켜짐
        val speeds = (0..40).map { SpeedSample(it * 1_000L, 30f) }
        val telltales = (0..40).map { sec ->
            val on = sec in 10..19 || sec >= 30
            TelltaleSample(sec * 1_000L, if (on) setOf("ABS") else emptySet())
        }

        // when
        val result = detector.detect(
            telltales = telltales,
            speeds = speeds,
            tripStartMs = 0,
            tripEndMs = 40_000,
        )

        // then
        assertEquals(
            listOf(
                SafetyWarningHit(occurredAtMs = 10_000, type = "ABS"),
                SafetyWarningHit(occurredAtMs = 30_000, type = "ABS"),
            ),
            result,
        )
    }

    @Test
    fun `정차 중에 켜진 경고등은 달리기 시작할 때 센다`() {
        // given
        // 0 ~ 19초 정차(0km/h), 20초부터 30km/h로 주행. ABS는 5초부터 계속 켜짐
        val speeds = (0..30).map { sec -> SpeedSample(sec * 1_000L, if (sec >= 20) 30f else 0f) }
        val telltales = (0..30).map { sec ->
            TelltaleSample(sec * 1_000L, if (sec >= 5) setOf("ABS") else emptySet())
        }

        // when
        val result = detector.detect(
            telltales = telltales,
            speeds = speeds,
            tripStartMs = 0,
            tripEndMs = 30_000,
        )

        // then
        assertEquals(listOf(SafetyWarningHit(occurredAtMs = 20_000, type = "ABS")), result)
    }
}
