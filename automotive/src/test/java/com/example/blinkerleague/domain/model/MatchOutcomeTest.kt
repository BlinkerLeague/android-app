package com.example.blinkerleague.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchOutcomeTest {

    @Test
    fun `득점이 실점보다 많으면 승리`() {
        // when
        val outcome = MatchOutcome.of(goals = 2, conceded = 1)

        // then
        assertEquals(MatchOutcome.WIN, outcome)
    }

    @Test
    fun `득점과 실점이 같으면 무승부`() {
        // when
        val outcome = MatchOutcome.of(goals = 1, conceded = 1)

        // then
        assertEquals(MatchOutcome.DRAW, outcome)
    }

    @Test
    fun `득점이 실점보다 적으면 아쉬운 경기`() {
        // when
        val outcome = MatchOutcome.of(goals = 1, conceded = 3)

        // then
        assertEquals(MatchOutcome.TOUGH, outcome)
    }

    @Test
    fun `실점 없이 득점만 있으면 완승`() {
        // when
        val outcome = MatchOutcome.of(goals = 3, conceded = 0)

        // then
        assertEquals(MatchOutcome.PERFECT, outcome)
    }

    @Test
    fun `회전이 없으면 승패가 없다`() {
        // when
        val outcome = MatchOutcome.of(goals = 0, conceded = 0)

        // then
        assertNull(outcome)
    }
}
