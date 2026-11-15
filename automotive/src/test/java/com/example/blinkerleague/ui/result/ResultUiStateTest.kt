package com.example.blinkerleague.ui.result

import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.MatchOutcome
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `적립 포인트가 0이면 포인트 줄을 보이지 않는다`() {
        // given
        // 점수는 났지만 적립된 포인트가 없는 운행 (첫 경기 등)
        val trip = result(ScoreState.SCORED, 67)

        // when
        val state = trip.toResultUiState(null, GradeThreshold.DEFAULT, earnedPoints = 0)

        // then
        assertNull(state.earnedPoints)
    }

    @Test
    fun `결과 화면 상태에 득점과 실점으로 정한 승패를 담는다`() {
        // given
        // 깜빡이 켬 · 안 켬 · 켬 → 2 대 1
        val trip = result(ScoreState.SCORED, 67, signals = listOf(true, false, true))

        // when
        val state = trip.toResultUiState(null, GradeThreshold.DEFAULT)

        // then
        assertEquals(MatchOutcome.WIN, state.outcome)
    }

    @Test
    fun `짧은 이동 결과에는 승패를 담지 않는다`() {
        // given
        // 회전은 있었지만(2 대 1) 짧은 이동이라 경기에서 제외됨
        val trip = result(ScoreState.EXCLUDED_SHORT, null, signals = listOf(true, false, true))

        // when
        val state = trip.toResultUiState(null, GradeThreshold.DEFAULT)

        // then
        assertNull(state.outcome)
    }

    @Test
    fun `기준값에서 가장 높은 등급이면 축하 연출을 켜고 그 아래 등급은 끈다`() {
        // given
        // 등급 이름이 아니라 기준값의 minScore 순서로 맨 위와 그다음 등급을 고름
        val grades = GradeThreshold.DEFAULT.sortedByDescending { it.minScore }
        val top = result(ScoreState.SCORED, grades[0].minScore)
        val second = result(ScoreState.SCORED, grades[1].minScore)

        // when
        val topState = top.toResultUiState(null, GradeThreshold.DEFAULT)
        val secondState = second.toResultUiState(null, GradeThreshold.DEFAULT)

        // then
        assertTrue(topState.celebrate)
        assertFalse(secondState.celebrate)
    }

    @Test
    fun `실점이 있으면 다음 경기 팁으로 깜빡이 팁을 담는다`() {
        // given
        // 깜빡이 켬 · 안 켬 · 켬 → 실점 1
        val trip = result(ScoreState.SCORED, 67, signals = listOf(true, false, true))
        val tips = mapOf("TURN_SIGNAL" to "회전 전에 깜빡이를 미리 켜 보세요")

        // when
        val state = trip.toResultUiState(null, GradeThreshold.DEFAULT, tips = tips)

        // then
        assertEquals("회전 전에 깜빡이를 미리 켜 보세요", state.tip)
    }

    @Test
    fun `실점이 없으면 다음 경기 팁을 비운다`() {
        // given
        // 회전 세 번 모두 깜빡이 켬 → 실점 0
        val trip = result(ScoreState.SCORED, 100, signals = listOf(true, true, true))
        val tips = mapOf("TURN_SIGNAL" to "회전 전에 깜빡이를 미리 켜 보세요")

        // when
        val state = trip.toResultUiState(null, GradeThreshold.DEFAULT, tips = tips)

        // then
        assertNull(state.tip)
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
