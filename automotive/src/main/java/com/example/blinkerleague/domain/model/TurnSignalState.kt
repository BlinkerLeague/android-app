package com.example.blinkerleague.domain.model

enum class TurnSignalState { OFF, LEFT, RIGHT, HAZARD }

/** 방향지시등 상태가 [state]로 바뀐 시각. 다음 샘플 전까지 같은 상태가 유지된다고 봄. */
data class SignalSample(
    val timestampMs: Long,
    val state: TurnSignalState,
)
