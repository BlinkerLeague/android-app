package com.example.blinkerleague.domain.model

/**
 * 끝난 운행 하나. [id]와 이벤트 id는 클라이언트에서 만든 UUID라서 재전송해도 서버에서 중복되지 않음.
 * 원시 위치 좌표는 담지 않음 (거리만).
 */
data class Trip(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceKm: Double,
    val supported: Set<SignalType>,
    val events: List<TripEvent>,
)

/** 운행 중 판정된 이벤트. 서버 `trip_event.type`에 대응. 2차에서 HARSH_*, SAFETY_WARNING, NIGHT_SEGMENT 추가 */
sealed interface TripEvent {
    val id: String
    val occurredAtMs: Long

    data class TurnEvent(
        override val id: String,
        override val occurredAtMs: Long,
        val direction: TurnDirection,
        val signalOn: Boolean,
        val uTurn: Boolean,
    ) : TripEvent
}
