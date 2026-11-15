package com.example.blinkerleague.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreItemTest {

    @Test
    fun `방향지시등 · 핸들 각도 · 속도가 모두 있으면 SIGNAL_CANCEL을 측정 항목에 넣는다`() {
        // given
        val signals = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE, SignalType.SPEED)

        // when
        val items = ScoreItem.measurable(signals)

        // then
        assertTrue("측정 항목: $items", ScoreItem.TURN_SIGNAL in items)
        assertTrue("측정 항목: $items", ScoreItem.SIGNAL_CANCEL in items)
    }

    @Test
    fun `방향지시등 · 핸들 각도 · 속도가 있으면 TURN_SPEED를 측정 항목에 넣는다`() {
        // given
        val signals = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE, SignalType.SPEED)

        // when
        val items = ScoreItem.measurable(signals)

        // then
        assertTrue("측정 항목: $items", ScoreItem.TURN_SPEED in items)
    }

    @Test
    fun `속도와 브레이크가 모두 있으면 SMOOTH_DRIVING을 넣는다`() {
        // given
        val signals = setOf(SignalType.SPEED, SignalType.BRAKE)

        // when
        val items = ScoreItem.measurable(signals)

        // then
        assertTrue("측정 항목: $items", ScoreItem.SMOOTH_DRIVING in items)
    }

    @Test
    fun `브레이크가 없으면 SMOOTH_DRIVING을 넣지 않는다`() {
        // given
        val signals = setOf(SignalType.SPEED)

        // when
        val items = ScoreItem.measurable(signals)

        // then
        assertFalse("측정 항목: $items", ScoreItem.SMOOTH_DRIVING in items)
    }
}
