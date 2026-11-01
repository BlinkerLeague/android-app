package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.Trip
import com.example.blinkerleague.domain.model.TripEvent

/**
 * 끝난 운행의 시계열을 모아 [Trip]으로 만듦: 회전 감지 → 깜빡이 판정 → 이벤트, 거리 계산.
 *
 * - 방향지시등(TURN_SIGNAL)을 지원하지 않는 차량이면 회전 이벤트를 만들지 않음 (서버에서 항목 제외)
 * - 위치는 운행 구간 안의 것만 씀. 신호는 회전 전 인정 구간 때문에 운행 시작 전 것도 같이 넘겨도 됨
 * - id 생성은 밖에서 주입 ([newId]) — 테스트에서 고정값을 쓸 수 있게
 */
class TripAssembler(
    config: DetectionConfig,
    private val newId: () -> String,
) {
    private val detector = TurnDetector(config.turn)
    private val judge = SignalJudge(config.turn)

    fun assemble(
        tripId: String,
        startedAtMs: Long,
        endedAtMs: Long,
        locations: List<LocationSample>,
        signals: List<SignalSample>,
        supported: Set<SignalType>,
    ): Trip {
        val inTrip = locations.filter { it.timestampMs in startedAtMs..endedAtMs }

        val turnEvents = if (SignalType.TURN_SIGNAL in supported) {
            judge.judge(detector.detect(inTrip), signals).map {
                TripEvent.TurnEvent(
                    id = newId(),
                    occurredAtMs = it.turn.startMs,
                    direction = it.turn.direction,
                    signalOn = it.signalOn,
                    uTurn = it.turn.uTurn,
                )
            }
        } else {
            emptyList()
        }

        return Trip(
            id = tripId,
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            distanceKm = TripDistance.km(inTrip),
            supported = supported,
            events = turnEvents,
        )
    }
}
