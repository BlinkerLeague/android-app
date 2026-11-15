package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.Coordinate
import com.example.blinkerleague.domain.model.HeadlightSample
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NightSegmentDetectorTest {

    private val seoulZone = ZoneId.of("Asia/Seoul")
    private val seoul = Coordinate(37.57, 126.98)

    private fun kst(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 11, 15, hour, minute, 0, 0, seoulZone).toInstant().toEpochMilli()

    @Test
    fun `야간 구간 동안 하향등이 꺼진 시간을 잰다`() {
        // given
        // 서울, 2026-11-15 22:00 ~ 22:10 KST 운행 (운행 전체가 밤)
        // 하향등: 21:59 꺼짐(운행 전 상태) → 22:02 켜짐 → 22:08 꺼짐. 꺼진 시간 2분 + 2분 = 240초
        val detector = NightSegmentDetector(seoulZone)
        val headlights = listOf(
            HeadlightSample(kst(21, 59), on = false),
            HeadlightSample(kst(22, 2), on = true),
            HeadlightSample(kst(22, 8), on = false),
        )

        // when
        val segments = detector.detect(headlights, tripStartMs = kst(22, 0), tripEndMs = kst(22, 10), coordinate = seoul)

        // then
        assertEquals(
            listOf(NightSegment(occurredAtMs = kst(22, 0), durationSec = 600, headlightOffSec = 240)),
            segments,
        )
    }
}
