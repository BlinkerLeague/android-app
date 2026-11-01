package com.example.blinkerleague.domain.model

enum class ScoreState {
    /** 아직 서버 점수를 받지 못함 (업로드 대기) */
    PENDING,
    SCORED,

    /** 측정할 회전이 없었음 */
    NO_TURN,

    /** 1km 미만이라 점수 대상이 아님 */
    EXCLUDED_SHORT,

    /** 서버가 업로드를 거부함 */
    REJECTED,
}

/** 정차 후 결과 화면에 보여줄 운행 결과 */
data class TripResult(
    val tripId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceKm: Double,
    val scoreState: ScoreState,
    val score: Int?,
    val turns: List<TurnRecord>,
)

data class TurnRecord(
    val occurredAtMs: Long,
    val direction: TurnDirection,
    val signalOn: Boolean,
)
