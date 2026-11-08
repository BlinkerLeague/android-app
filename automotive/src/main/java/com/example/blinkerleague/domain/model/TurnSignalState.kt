package com.example.blinkerleague.domain.model

/**
 * 방향지시등 상태. [HAZARD]는 도메인 모델에만 있는 값임 — Vehicle SDK `TurnSignalValue`는 OFF/RIGHT/LEFT뿐이고,
 * 비상등을 켜도 TurnSignal 콜백이 바뀌지 않음(에뮬레이터 실측). 비상등은 TurnSignal로 감지 불가.
 */
enum class TurnSignalState { OFF, LEFT, RIGHT, HAZARD }

/** 방향지시등 상태가 [state]로 바뀐 시각. 다음 샘플 전까지 같은 상태가 유지된다고 봄. */
data class SignalSample(
    val timestampMs: Long,
    val state: TurnSignalState,
)
