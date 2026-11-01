package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.data.scenario.scenario
import com.example.blinkerleague.domain.config.TurnConfig
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.TurnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnDetectorTest {

    private val config = TurnConfig()
    private val detector = TurnDetector(config)

    @Test
    fun `직진만 하면 회전 없음`() {
        val s = scenario("직진") {
            start()
            straight(seconds = 60, speedKmh = 50f)
        }
        assertTrue(detector.detect(s.locations).isEmpty())
    }

    @Test
    fun `90도 좌회전 감지 - 시작과 끝은 heading이 바뀐 구간`() {
        val s = scenario("좌회전") {
            start()
            straight(seconds = 20, speedKmh = 40f)
            turn(angleDeg = -90f, seconds = 6, speedKmh = 20f)
            straight(seconds = 20, speedKmh = 40f)
        }
        val turns = detector.detect(s.locations)

        assertEquals(1, turns.size)
        val turn = turns.single()
        assertEquals(TurnDirection.LEFT, turn.direction)
        assertEquals(90f, turn.angleDeg, 0.1f)
        assertFalse(turn.uTurn)
        assertEquals(20_000L, turn.startMs)
        assertEquals(26_000L, turn.endMs)
    }

    @Test
    fun `우회전은 RIGHT`() {
        val s = scenario("우회전") {
            start()
            straight(seconds = 10, speedKmh = 30f)
            turn(angleDeg = 90f, seconds = 5, speedKmh = 20f)
            straight(seconds = 10, speedKmh = 30f)
        }
        assertEquals(TurnDirection.RIGHT, detector.detect(s.locations).single().direction)
    }

    @Test
    fun `150도 이상이면 유턴`() {
        val s = scenario("유턴") {
            start()
            straight(seconds = 10, speedKmh = 30f)
            turn(angleDeg = -180f, seconds = 8, speedKmh = 10f)
            straight(seconds = 10, speedKmh = 30f)
        }
        val turn = detector.detect(s.locations).single()
        assertEquals(TurnDirection.LEFT, turn.direction)
        assertTrue(turn.uTurn)
    }

    @Test
    fun `10초에 60도가 안 되는 완만한 커브는 회전이 아님`() {
        val s = scenario("커브") {
            start()
            straight(seconds = 10, speedKmh = 60f)
            turn(angleDeg = 60f, seconds = 20, speedKmh = 60f) // 10초에 30도
            straight(seconds = 10, speedKmh = 60f)
        }
        assertTrue(detector.detect(s.locations).isEmpty())
    }

    @Test
    fun `5km_h 미만에서 꺾은 것은 제외`() {
        val s = scenario("저속") {
            start()
            straight(seconds = 10, speedKmh = 30f)
            turn(angleDeg = 90f, seconds = 4, speedKmh = 4f)
            straight(seconds = 10, speedKmh = 30f)
        }
        assertTrue(detector.detect(s.locations).isEmpty())
    }

    @Test
    fun `heading 359에서 1로 넘어가도 2도 변화로 계산`() {
        assertEquals(2f, TurnDetector.headingDelta(359f, 1f), 0.001f)
        assertEquals(-2f, TurnDetector.headingDelta(1f, 359f), 0.001f)
        assertEquals(180f, TurnDetector.headingDelta(0f, 180f), 0.001f)
    }

    @Test
    fun `북쪽을 지나며 도는 우회전도 하나로 감지`() {
        val samples = listOf(330f, 330f, 345f, 0f, 15f, 30f, 30f).mapIndexed { i, h ->
            LocationSample(i * 1_000L, 0.0, 0.0, speedKmh = 20f, headingDeg = h)
        }
        val turn = detector.detect(samples).single()
        assertEquals(TurnDirection.RIGHT, turn.direction)
        assertEquals(60f, turn.angleDeg, 0.1f)
    }

    @Test
    fun `직진 중 heading 흔들림은 회전으로 보지 않음`() {
        val samples = (0 until 60).map { i ->
            LocationSample(i * 1_000L, 0.0, 0.0, speedKmh = 40f, headingDeg = if (i % 2 == 0) 88f else 92f)
        }
        assertTrue(detector.detect(samples).isEmpty())
    }

    @Test
    fun `같은 방향 회전이 연달아 오면 하나로 합침`() {
        val s = scenario("ㄷ자") {
            start()
            straight(seconds = 10, speedKmh = 30f)
            turn(angleDeg = 90f, seconds = 5, speedKmh = 20f)
            straight(seconds = 2, speedKmh = 20f)
            turn(angleDeg = 90f, seconds = 5, speedKmh = 20f)
            straight(seconds = 10, speedKmh = 30f)
        }
        val turn = detector.detect(s.locations).single()
        assertEquals(180f, turn.angleDeg, 0.1f)
        assertTrue(turn.uTurn)
    }

    @Test
    fun `간격이 충분히 떨어진 두 회전은 따로 감지`() {
        val s = scenario("두 번") {
            start()
            straight(seconds = 10, speedKmh = 30f)
            turn(angleDeg = -90f, seconds = 5, speedKmh = 20f)
            straight(seconds = 15, speedKmh = 40f)
            turn(angleDeg = 90f, seconds = 5, speedKmh = 20f)
            straight(seconds = 10, speedKmh = 30f)
        }
        assertEquals(
            listOf(TurnDirection.LEFT, TurnDirection.RIGHT),
            detector.detect(s.locations).map { it.direction },
        )
    }

    @Test
    fun `좌우가 섞인 연속 회전은 제외`() {
        val s = scenario("S자") {
            start()
            straight(seconds = 10, speedKmh = 30f)
            turn(angleDeg = 70f, seconds = 4, speedKmh = 20f)
            turn(angleDeg = -70f, seconds = 4, speedKmh = 20f)
            straight(seconds = 10, speedKmh = 30f)
        }
        assertTrue(detector.detect(s.locations).isEmpty())
    }

    @Test
    fun `heading이 없는 샘플은 건너뜀`() {
        val samples = (0 until 20).map { i ->
            LocationSample(i * 1_000L, 0.0, 0.0, speedKmh = 30f, headingDeg = null)
        }
        assertTrue(detector.detect(samples).isEmpty())
    }
}
