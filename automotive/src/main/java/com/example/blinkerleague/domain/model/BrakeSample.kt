package com.example.blinkerleague.domain.model

/**
 * 브레이크 페달 눌림 상태가 [pressed]로 바뀐 시각.
 */
data class BrakeSample(
    val timestampMs: Long,
    val pressed: Boolean,
)
