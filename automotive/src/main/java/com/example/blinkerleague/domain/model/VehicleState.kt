package com.example.blinkerleague.domain.model

enum class Gear { P, R, N, D, UNKNOWN }

/**
 * 기어(와 알 수 있으면 시동) 상태가 바뀐 시각. 운행 구분([com.example.blinkerleague.domain.detection.TripSegmenter])의 입력.
 *
 * @property ignitionOn 시동 상태. **Pleos Vehicle SDK에는 시동 상태 API가 없어** 실제 공급원은 null(알 수 없음)을 줌
 *   (docs/sdk-test-log.md 스파이크 8). 시나리오·테스트처럼 알 수 있을 때만 true/false
 */
data class VehicleStateSample(
    val timestampMs: Long,
    val ignitionOn: Boolean?,
    val gear: Gear,
)
