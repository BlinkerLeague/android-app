package com.example.blinkerleague.domain.model

enum class Gear { P, R, N, D, UNKNOWN }

/** 시동·기어 상태가 바뀐 시각. 운행 구분([com.example.blinkerleague.domain.detection.TripSegmenter])의 입력. */
data class VehicleStateSample(
    val timestampMs: Long,
    val ignitionOn: Boolean,
    val gear: Gear,
)
