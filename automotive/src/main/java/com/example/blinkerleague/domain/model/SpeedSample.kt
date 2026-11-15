package com.example.blinkerleague.domain.model

/**
 * 차량 속도. 실제 공급원은 CarProperty `PERF_VEHICLE_SPEED`(m/s, 약 10Hz)를 km/h로 바꿔 줌.
 * 후진은 음수일 수 있어서, 거리·저속 판단에는 절댓값을 씀.
 */
data class SpeedSample(
    val timestampMs: Long,
    val speedKmh: Float,
)
