package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.Trip
import com.example.blinkerleague.domain.model.TripEvent
import com.example.blinkerleague.domain.model.VehicleStateSample

/** 운행 한 번 동안 모은 시계열. 신호·기어는 회전 전 구간 판정 때문에 운행 시작 전 것이 섞여 있어도 됨 */
data class TripRecording(
    val steering: List<SteeringSample>,
    val signals: List<SignalSample>,
    val vehicleStates: List<VehicleStateSample>,
    val locations: List<LocationSample>,
    /** 차량 속도. 거리(적분)와 저속 회전 제외에 씀. 없으면 위치로 계산함 */
    val speeds: List<SpeedSample> = emptyList(),
    /** 브레이크 페달 상태 변화. 급감속 판정에 씀 */
    val brakes: List<BrakeSample> = emptyList(),
)

/**
 * 끝난 운행의 시계열을 모아 [Trip]으로 만듦: 핸들 각도로 회전 감지 → 깜빡이 판정 → 이벤트, 거리 계산(속도 적분 우선).
 *
 * - 방향지시등(TURN_SIGNAL)과 핸들 각도(STEERING_ANGLE)가 모두 지원돼야 회전 이벤트를 만듦 (아니면 서버에서 항목 제외)
 * - 거기에 속도(SPEED)까지 지원되면 깜빡이를 켠 채 잊은 이벤트도 만듦
 * - 이벤트는 발생 시각 순으로 정렬함
 * - 핸들 각도·위치는 운행 구간 안의 것만 씀
 * - id 생성은 밖에서 주입 ([newId]) — 테스트에서 고정값을 쓸 수 있게
 */
class TripAssembler(
    config: DetectionConfig,
    private val newId: () -> String,
) {
    private val detector = SteeringTurnDetector(config.turn)
    private val judge = SignalJudge(config.turn)
    private val signalCancelDetector = SignalCancelDetector(config.signalCancel)

    fun assemble(
        tripId: String,
        startedAtMs: Long,
        endedAtMs: Long,
        recording: TripRecording,
        supported: Set<SignalType>,
    ): Trip {
        val window = startedAtMs..endedAtMs
        val locations = recording.locations.filter { it.timestampMs in window }

        val canMeasureTurns = SignalType.TURN_SIGNAL in supported && SignalType.STEERING_ANGLE in supported
        val turns = if (canMeasureTurns) {
            detector.detect(
                steering = recording.steering.filter { it.timestampMs in window },
                vehicleStates = recording.vehicleStates,
                signals = recording.signals,
                locations = locations,
                speeds = recording.speeds,
            )
        } else {
            emptyList()
        }
        val turnEvents = if (canMeasureTurns) {
            judge.judge(turns, recording.signals).map {
                TripEvent.TurnEvent(
                    id = newId(),
                    occurredAtMs = it.turn.startMs,
                    direction = it.turn.direction,
                    signalOn = it.signalOn,
                    uTurn = it.turn.uTurn,
                    maxSpeedKmh = TurnSpeed.maxKmh(it.turn, recording.speeds),
                )
            }
        } else {
            emptyList()
        }
        val signalLeftOnEvents = if (canMeasureTurns && SignalType.SPEED in supported) {
            signalCancelDetector.detect(recording.signals, turns, recording.speeds, startedAtMs, endedAtMs).map {
                TripEvent.SignalLeftOnEvent(id = newId(), occurredAtMs = it.occurredAtMs, durationSec = it.durationSec)
            }
        } else {
            emptyList()
        }

        return Trip(
            id = tripId,
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            distanceKm = if (recording.speeds.isNotEmpty()) {
                TripDistance.kmFromSpeeds(recording.speeds, startedAtMs, endedAtMs)
            } else {
                TripDistance.km(locations)
            },
            supported = supported,
            events = (turnEvents + signalLeftOnEvents).sortedBy { it.occurredAtMs },
        )
    }
}
