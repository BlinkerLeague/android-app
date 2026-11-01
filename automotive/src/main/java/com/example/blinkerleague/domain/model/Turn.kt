package com.example.blinkerleague.domain.model

enum class TurnDirection { LEFT, RIGHT }

/**
 * 감지된 회전 한 번.
 *
 * @property angleDeg 회전한 각도(양수). 방향은 [direction]
 */
data class Turn(
    val startMs: Long,
    val endMs: Long,
    val direction: TurnDirection,
    val angleDeg: Float,
    val uTurn: Boolean,
)

/** 깜빡이 판정 결과. 서버 `TURN` 이벤트 payload의 `direction`, `signalOn`, `uTurn`에 대응. */
data class JudgedTurn(
    val turn: Turn,
    val signalOn: Boolean,
)
