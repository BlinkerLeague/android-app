package com.example.blinkerleague.domain.model

/** 운전석 안전벨트 착용 상태를 읽은 시점. [buckled]는 채워져 있는지 */
data class SeatBeltSample(
    val timestampMs: Long,
    val buckled: Boolean,
)
