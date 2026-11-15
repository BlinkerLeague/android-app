package com.example.blinkerleague.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreItemTest {

    @Test
    fun `방향지시등 · 핸들 각도 · 속도가 모두 있으면 SIGNAL_CANCEL을 측정 항목에 넣는다`() {
        // given
        val signals = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE, SignalType.SPEED)

        // when
        val items = ScoreItem.measurable(signals)

        // then
        assertEquals(setOf(ScoreItem.TURN_SIGNAL, ScoreItem.SIGNAL_CANCEL), items)
    }
}
