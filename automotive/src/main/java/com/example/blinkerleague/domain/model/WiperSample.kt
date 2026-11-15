package com.example.blinkerleague.domain.model

/** 와이퍼 상태가 바뀐 시점. [on]은 작동 중인지 */
data class WiperSample(
    val timestampMs: Long,
    val on: Boolean,
)
