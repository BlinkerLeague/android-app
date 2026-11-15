package com.example.blinkerleague.domain.model

import org.junit.Assert.assertEquals
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
}
