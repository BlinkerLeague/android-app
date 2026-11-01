package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.TurnConfig
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.Turn
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnSignalState
import com.example.blinkerleague.domain.model.TurnSignalState.HAZARD
import com.example.blinkerleague.domain.model.TurnSignalState.LEFT
import com.example.blinkerleague.domain.model.TurnSignalState.OFF
import com.example.blinkerleague.domain.model.TurnSignalState.RIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalJudgeTest {

    private val judge = SignalJudge(TurnConfig(signalLeadSec = 5))

    // 회전 구간 20s ~ 26s, 인정 구간 15s ~ 26s
    private val leftTurn = Turn(20_000, 26_000, TurnDirection.LEFT, 90f, uTurn = false)

    private fun signalOn(turn: Turn, vararg signals: Pair<Long, TurnSignalState>): Boolean =
        judge.judge(listOf(turn), signals.map { SignalSample(it.first, it.second) }).single().signalOn

    @Test
    fun `회전 직전에 같은 방향을 켜면 성공`() {
        assertTrue(signalOn(leftTurn, 17_000L to LEFT, 27_000L to OFF))
    }

    @Test
    fun `회전 도중에 켜도 성공`() {
        assertTrue(signalOn(leftTurn, 24_000L to LEFT))
    }

    @Test
    fun `인정 구간 전에 켜서 계속 켜져 있으면 성공`() {
        assertTrue(signalOn(leftTurn, 5_000L to LEFT, 30_000L to OFF))
    }

    @Test
    fun `인정 구간 전에 켰다가 끄면 실패`() {
        assertFalse(signalOn(leftTurn, 5_000L to LEFT, 10_000L to OFF))
    }

    @Test
    fun `정확히 인정 구간 시작에 꺼지면 실패`() {
        assertFalse(signalOn(leftTurn, 5_000L to LEFT, 15_000L to OFF))
    }

    @Test
    fun `회전이 끝난 뒤에 켜면 실패`() {
        assertFalse(signalOn(leftTurn, 26_001L to LEFT))
    }

    @Test
    fun `반대 방향을 켜면 실패`() {
        assertFalse(signalOn(leftTurn, 18_000L to RIGHT))
    }

    @Test
    fun `비상등은 인정하지 않음`() {
        assertFalse(signalOn(leftTurn, 18_000L to HAZARD))
    }

    @Test
    fun `신호 기록이 없으면 실패`() {
        assertFalse(signalOn(leftTurn))
    }

    @Test
    fun `깜빡임 on-off가 반복돼도 한 번 켜지면 성공`() {
        assertTrue(signalOn(leftTurn, 19_000L to LEFT, 19_500L to OFF, 20_000L to LEFT, 20_500L to OFF))
    }

    @Test
    fun `신호 순서가 섞여 들어와도 시각순으로 판정`() {
        assertFalse(signalOn(leftTurn, 10_000L to OFF, 5_000L to LEFT))
    }

    @Test
    fun `회전마다 따로 판정`() {
        val rightTurn = Turn(60_000, 65_000, TurnDirection.RIGHT, 90f, uTurn = false)
        val result = judge.judge(
            listOf(leftTurn, rightTurn),
            listOf(SignalSample(18_000, LEFT), SignalSample(27_000, OFF)),
        )
        assertEquals(listOf(true, false), result.map { it.signalOn })
    }
}
