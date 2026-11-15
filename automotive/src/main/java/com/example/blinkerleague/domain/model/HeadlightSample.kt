package com.example.blinkerleague.domain.model

/** 하향등(전조등) 상태가 바뀐 시점. [on]은 켜져 있는지 */
data class HeadlightSample(
    val timestampMs: Long,
    val on: Boolean,
)
