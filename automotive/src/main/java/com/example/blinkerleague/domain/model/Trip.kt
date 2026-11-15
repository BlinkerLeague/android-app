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
        val uTurn: Boolean?,
        /** 회전 구간 최고 속도(km/h). 속도를 모르면 null */
        val maxSpeedKmh: Double? = null,
    ) : TripEvent

    /** 깜빡이를 켠 채 잊음. [durationSec]는 회전 뒤 minSpeedKmh 이상으로 달리며 켜 둔 시간 */
    data class SignalLeftOnEvent(
        override val id: String,
        override val occurredAtMs: Long,
        val durationSec: Int,
    ) : TripEvent

    /** 급가속 */
    data class HarshAccelEvent(
        override val id: String,
        override val occurredAtMs: Long,
    ) : TripEvent

    /** 급감속 (브레이크를 밟은 채 1초 사이 속도가 기준 이상 줄어듦) */
    data class HarshBrakeEvent(
        override val id: String,
        override val occurredAtMs: Long,
    ) : TripEvent

    /** 야간 주행 구간. [durationSec]는 운행 중 일몰 ~ 일출에 걸친 시간, [headlightOffSec]는 그 동안 하향등이 꺼져 있던 시간 */
    data class NightSegmentEvent(
        override val id: String,
        override val occurredAtMs: Long,
        val durationSec: Int,
        val headlightOffSec: Int,
    ) : TripEvent

    /** 안전벨트 (운행당 1건). [occurredAtMs]는 출발 시각, [unbuckledMovingSec]는 주행 중 벨트를 안 맨 시간 */
    data class SeatBeltEvent(
        override val id: String,
        override val occurredAtMs: Long,
        val buckledAtDeparture: Boolean,
        val unbuckledMovingSec: Int,
    ) : TripEvent

    /** 안전 경고등 한 번 (주행 중 설정 대상 경고등이 켜짐). 횟수만 보내므로 경고등 이름은 담지 않음 */
    data class SafetyWarningEvent(
        override val id: String,
        override val occurredAtMs: Long,
    ) : TripEvent
}
