package com.example.blinkerleague.domain.model

/** 그 시점에 켜져 있는 경고등 이름 집합. 이름은 SDK 타입이 아니라 문자열로 받음 */
data class TelltaleSample(
    val timestampMs: Long,
    val active: Set<String>,
)
