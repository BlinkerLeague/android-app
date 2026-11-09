package com.example.blinkerleague.ui.result

import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ResultUiStateTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val start = ZonedDateTime.of(2026, 11, 8, 8, 10, 0, 0, zone).toInstant().toEpochMilli()
    private val all = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE)

    private fun result(
        state: ScoreState,
        score: Int?,
        signals: List<Boolean> = listOf(true, false, true),
        supported: Set<SignalType> = all,
    ) = TripResult(
        tripId = "t",
        startedAtMs = start,
        endedAtMs = start + 32 * 60_000,
        distanceKm = 14.2,
        scoreState = state,
        score = score,
        // 일부러 역순: 타임라인은 시각순이어야 함
        turns = signals.mapIndexed { i, on ->
            TurnRecord(start + (5 + i * 6) * 60_000L, if (i % 2 == 0) TurnDirection.LEFT else TurnDirection.RIGHT, on)
        }.reversed(),
        supported = supported,
    )

    private fun metric(state: ResultUiState.Loaded) = state.metrics.first { it.item == MetricItem.TURN_SIGNAL }.value

    @Test
    fun `점수 - 등급, 득점·실점, 깜빡이만 값이 있고 나머지는 준비 중`() {
        val state = result(ScoreState.SCORED, 67).toResultUiState(null, GradeThreshold.DEFAULT)
        assertEquals("C", state.grade)
        assertEquals(2, state.goals)
        assertEquals(1, state.conceded)
        assertEquals(MetricValue.Score(67), metric(state))
        assertEquals(3, state.metrics.count { it.value == MetricValue.ComingSoon })
    }

    @Test
    fun `적립 포인트는 점수가 난 운행만 표시`() {
        assertEquals(12, result(ScoreState.SCORED, 67).toResultUiState(null, GradeThreshold.DEFAULT, earnedPoints = 12).earnedPoints)
        assertNull(result(ScoreState.EXCLUDED_SHORT, null).toResultUiState(null, GradeThreshold.DEFAULT, earnedPoints = 0).earnedPoints)
    }

    @Test
    fun `업로드 전이면 집계 중, 50점 미만은 등급 없음`() {
        assertEquals(MetricValue.Pending, metric(result(ScoreState.PENDING, null).toResultUiState(null, GradeThreshold.DEFAULT)))
        assertNull(result(ScoreState.SCORED, 33).toResultUiState(null, GradeThreshold.DEFAULT).grade)
    }

    @Test
    fun `짧은 이동·업로드 거부는 집계 중이 아님`() {
        assertEquals(MetricValue.Excluded, metric(result(ScoreState.EXCLUDED_SHORT, null, emptyList()).toResultUiState(null, GradeThreshold.DEFAULT)))
        assertEquals(MetricValue.Excluded, metric(result(ScoreState.EXCLUDED_SHORT, null).toResultUiState(null, GradeThreshold.DEFAULT)))
        assertEquals(MetricValue.NotScored, metric(result(ScoreState.REJECTED, null).toResultUiState(null, GradeThreshold.DEFAULT)))
    }

    @Test
    fun `회전 없이 다른 항목으로 점수가 나도 깜빡이 항목은 회전 없음 (서버 D28)`() {
        val state = result(ScoreState.SCORED, 80, signals = emptyList()).toResultUiState(null, GradeThreshold.DEFAULT)
        assertEquals(MetricValue.NoTurn, metric(state))
        assertEquals("B", state.grade)
    }

    @Test
    fun `회전 없음과 미지원은 0점이 아니라 제외로`() {
        val noTurn = result(ScoreState.NO_TURN, null, signals = emptyList()).toResultUiState(null, GradeThreshold.DEFAULT)
        assertEquals(MetricValue.NoTurn, metric(noTurn))
        assertNull(noTurn.grade)

        val noSteering = result(ScoreState.NO_TURN, null, emptyList(), setOf(SignalType.TURN_SIGNAL)).toResultUiState(null, GradeThreshold.DEFAULT)
        assertEquals(MetricValue.Unsupported, metric(noSteering))
    }

    @Test
    fun `타임라인 - 시각순 카드, 득점은 깜빡이 켠 회전`() {
        val plays = result(ScoreState.SCORED, 67).toPlaysUiState(zone)
        assertEquals("08:10", plays.startedAt)
        assertEquals("08:42", plays.endedAt)
        assertEquals(listOf("08:15", "08:21", "08:27"), plays.plays.map { it.time })
        assertEquals(listOf(true, false, true), plays.plays.map { it.goal })
        assertEquals(TurnDirection.RIGHT, plays.plays[1].direction)
    }
}
