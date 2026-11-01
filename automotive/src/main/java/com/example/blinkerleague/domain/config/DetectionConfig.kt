package com.example.blinkerleague.domain.config

/**
 * 측정 기준값. 서버 `GET /api/config/thresholds`의 `trip`, `turn`과 같은 이름·단위를 씀.
 * 서버 값을 받지 못하면 [DEFAULT]를 사용함.
 */
data class DetectionConfig(
    val version: Int = 0,
    val trip: TripConfig = TripConfig(),
    val turn: TurnConfig = TurnConfig(),
) {
    companion object {
        val DEFAULT = DetectionConfig()
    }
}

data class TripConfig(
    val minDistanceKm: Double = 1.0,
    val parkEndMinutes: Int = 3,
) {
    val parkEndMs: Long get() = parkEndMinutes * 60_000L
}

data class TurnConfig(
    val minSpeedKmh: Float = 5f,
    val windowSec: Int = 10,
    val minAngleDeg: Float = 60f,
    val uTurnAngleDeg: Float = 150f,
    val signalLeadSec: Int = 5,
    // 아래는 앱 내부 튜닝값 (서버 설정에는 아직 없음)
    /** 이보다 작은 heading 변화는 직진 중 흔들림으로 보고 회전 시작·끝 계산에서 무시함 */
    val headingNoiseDeg: Float = 2f,
    /** 회전 사이 간격이 이 안이면 연속 회전으로 묶음. 방향이 섞이면(회전교차로, S자) 제외 */
    val mergeGapSec: Int = 3,
) {
    val windowMs: Long get() = windowSec * 1_000L
    val signalLeadMs: Long get() = signalLeadSec * 1_000L
    val mergeGapMs: Long get() = mergeGapSec * 1_000L
}
