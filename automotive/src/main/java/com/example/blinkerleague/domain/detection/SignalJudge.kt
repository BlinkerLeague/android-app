package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.TurnConfig
import com.example.blinkerleague.domain.model.JudgedTurn
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.Turn
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnSignalState

/**
 * 회전마다 깜빡이를 제때 켰는지 판정함.
 *
 * 회전 시작 [TurnConfig.signalLeadSec]초 전부터 회전이 끝날 때까지,
 * 같은 방향 방향지시등이 한 번이라도 켜져 있었으면 성공. 비상등(HAZARD)은 인정하지 않음.
 * 신호 샘플은 상태가 바뀐 시각이므로, 다음 샘플 전까지 그 상태가 이어진다고 봄.
 */
class SignalJudge(private val config: TurnConfig) {

    fun judge(turns: List<Turn>, signals: List<SignalSample>): List<JudgedTurn> {
        val sorted = signals.sortedBy { it.timestampMs }
        return turns.map { JudgedTurn(it, isSignalOn(it, sorted)) }
    }

    private fun isSignalOn(turn: Turn, sortedSignals: List<SignalSample>): Boolean {
        val target = when (turn.direction) {
            TurnDirection.LEFT -> TurnSignalState.LEFT
            TurnDirection.RIGHT -> TurnSignalState.RIGHT
        }
        val windowStart = turn.startMs - config.signalLeadMs
        val windowEnd = turn.endMs
        return sortedSignals.indices.any { i ->
            val s = sortedSignals[i]
            val stateEnd = sortedSignals.getOrNull(i + 1)?.timestampMs ?: Long.MAX_VALUE
            s.state == target && s.timestampMs <= windowEnd && stateEnd > windowStart
        }
    }
}
