package com.example.blinkerleague.ui.result

import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.MetricDetail
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MetricDetailTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val trip = TripResult(
        "t", 0, 60_000, 5.0, ScoreState.SCORED, 67,
        listOf(TurnRecord(1_000, TurnDirection.LEFT, true), TurnRecord(2_000, TurnDirection.RIGHT, false), TurnRecord(3_000, TurnDirection.LEFT, true)),
        setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE),
    )
    private val tips = DetectionConfig.DEFAULT_TIPS

    @Test
    fun `깜빡이 - 서버 판정 근거가 있으면 그것, 회전 목록과 팁`() {
        val server = listOf(MetricDetail("TURN_SIGNAL", "SCORED", null, 67, mapOf("turns" to 3, "onTime" to 1, "late" to 1, "missed" to 1)))
        val detail = trip.toMetricDetail(MetricItem.TURN_SIGNAL, server, GradeThreshold.DEFAULT, tips, zone)
        assertEquals(MetricValue.Score(67), detail.value)
        assertEquals(TurnCounts(3, 1, 1, 1, fromServer = true), detail.turnCounts)
        assertEquals(3, detail.turns.size)
        assertEquals("회전 5초 전에 깜빡이를 켜 보세요.", detail.tip)
    }

    @Test
    fun `서버 근거를 받기 전에는 기기 기록으로 셈`() {
        val detail = trip.toMetricDetail(MetricItem.TURN_SIGNAL, null, GradeThreshold.DEFAULT, tips, zone)
        assertEquals(TurnCounts(3, 2, 0, 1, fromServer = false), detail.turnCounts)
    }

    @Test
    fun `다른 항목 - 준비 중, 서버가 미지원 이유를 주면 함께, 회전 목록 없음`() {
        val server = listOf(MetricDetail("SMOOTH_DRIVING", "UNSUPPORTED", "SIGNAL_NOT_SUPPORTED", null, emptyMap()))
        val detail = trip.toMetricDetail(MetricItem.SMOOTH_DRIVING, server, GradeThreshold.DEFAULT, tips, zone)
        assertEquals(MetricValue.ComingSoon, detail.value)
        assertEquals("SIGNAL_NOT_SUPPORTED", detail.serverReason)
        assertNull(detail.turnCounts)
        assertTrue(detail.turns.isEmpty())
    }

    @Test
    fun `서버가 준 적용 가중치를 백분율로 보여 준다`() {
        // given
        val server = listOf(
            MetricDetail("TURN_SIGNAL", "SCORED", null, 67, mapOf("turns" to 3, "onTime" to 1, "late" to 1, "missed" to 1), weight = 0.4),
        )

        // when
        val detail = trip.toMetricDetail(MetricItem.TURN_SIGNAL, server, GradeThreshold.DEFAULT, tips, zone)

        // then
        assertEquals(40, detail.weightPercent)
    }

    @Test
    fun `UNSUPPORTED 항목은 서버가 가중치를 줘도 보이지 않는다`() {
        // given
        val server = listOf(
            MetricDetail("SMOOTH_DRIVING", "UNSUPPORTED", "SIGNAL_NOT_SUPPORTED", null, emptyMap(), weight = 0.3),
        )

        // when
        val detail = trip.toMetricDetail(MetricItem.SMOOTH_DRIVING, server, GradeThreshold.DEFAULT, tips, zone)

        // then
        assertNull(detail.weightPercent)
    }
}
