package com.example.blinkerleague.domain.model

enum class TurnDirection { LEFT, RIGHT }

/**
 * 감지된 회전 한 번.
 *
 * @property angleDeg 회전 크기(양수). 핸들 감지([com.example.blinkerleague.domain.detection.SteeringTurnDetector])는
 *   최대 핸들 각도, 진행 방향 감지([com.example.blinkerleague.domain.detection.HeadingTurnDetector])는 heading 변화량
 * @property uTurn 유턴 여부. 핸들 각도만으로는 알 수 없어서 null
 */
data class Turn(
    val startMs: Long,
    val endMs: Long,
    val direction: TurnDirection,
    val angleDeg: Float,
    val uTurn: Boolean?,
)

/** 깜빡이 판정 결과. 서버 `TURN` 이벤트 payload의 `direction`, `signalOn`, `uTurn`에 대응. */
data class JudgedTurn(
    val turn: Turn,
    val signalOn: Boolean,
)
