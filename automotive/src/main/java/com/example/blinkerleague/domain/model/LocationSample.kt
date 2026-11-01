package com.example.blinkerleague.domain.model

/**
 * 위치 한 점. 원시 좌표는 앱 안에서만 쓰고 서버로 보내지 않음.
 *
 * @property headingDeg 진행 방향(0~360, 북=0, 시계 방향). 정차 중에는 없을 수 있음
 * @property speedKmh 속도. 제공되지 않으면 null
 */
data class LocationSample(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float?,
    val headingDeg: Float?,
)
