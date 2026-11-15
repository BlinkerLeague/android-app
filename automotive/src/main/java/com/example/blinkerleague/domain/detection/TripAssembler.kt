package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.Coordinate
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.HeadlightSample
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.SeatBeltSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.TelltaleSample
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.Trip
import com.example.blinkerleague.domain.model.TripEvent
import com.example.blinkerleague.domain.model.VehicleStateSample
import java.time.ZoneId
import java.time.ZoneOffset

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
    /** 하향등 상태 변화. 야간 전조등 판정에 씀 */
    val headlights: List<HeadlightSample> = emptyList(),
    /** 운전석 안전벨트 착용 상태를 읽은 값. 안전벨트 판정에 씀 */
    val seatBelts: List<SeatBeltSample> = emptyList(),
    /** 그 시점에 켜진 경고등 이름 집합 (1초 폴링). 안전 경고등 판정에 씀 */
    val telltales: List<TelltaleSample> = emptyList(),
)

/**
 * 끝난 운행의 시계열을 모아 [Trip]으로 만듦: 핸들 각도로 회전 감지 → 깜빡이 판정 → 이벤트, 거리 계산(속도 적분 우선).
 *
 * - 방향지시등(TURN_SIGNAL)과 핸들 각도(STEERING_ANGLE)가 모두 지원돼야 회전 이벤트를 만듦 (아니면 서버에서 항목 제외)
 * - 거기에 속도(SPEED)까지 지원되면 깜빡이를 켠 채 잊은 이벤트도 만듦
 * - 속도(SPEED)와 브레이크(BRAKE)가 지원되면 급가속 · 급감속 이벤트를 만듦 (운행 구간 안의 것만)
 * - 하향등(HEADLIGHT)이 지원되고 대표 좌표가 있으면 야간 구간 이벤트를 만듦
 * - 안전벨트(SEAT_BELT)와 속도(SPEED)가 지원되면 운행당 안전벨트 이벤트 하나를 출발 시각에 만듦 (출발 시점 상태를 모르면 만들지 않고 supported에서 SEAT_BELT를 뺌)
 * - 안전 경고등(SAFETY_WARNING)과 속도(SPEED)가 지원되면 주행 중 켜진 경고등마다 안전 경고 이벤트를 만듦
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
    private val harshDetector = HarshDrivingDetector(config.harsh)
    private val seatBeltJudge = SeatBeltJudge(config.seatBelt)
    private val safetyWarningDetector = SafetyWarningDetector(config.safetyWarning)

    fun assemble(
        tripId: String,
        startedAtMs: Long,
        endedAtMs: Long,
        recording: TripRecording,
        supported: Set<SignalType>,
        /** 등록 지역의 대표 좌표 (야간 판정용 일출 · 일몰 계산). 없으면 야간 구간을 만들지 않음 */
        coordinate: Coordinate? = null,
        /** 일출 · 일몰 날짜를 정하는 시간대 */
        zone: ZoneId = ZoneOffset.UTC,
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
        val harshEvents = if (SignalType.SPEED in supported && SignalType.BRAKE in supported) {
            harshDetector.detect(recording.speeds, recording.brakes).filter { it.occurredAtMs in window }.map {
                when (it.kind) {
                    HarshKind.ACCEL -> TripEvent.HarshAccelEvent(id = newId(), occurredAtMs = it.occurredAtMs)
                    HarshKind.BRAKE -> TripEvent.HarshBrakeEvent(id = newId(), occurredAtMs = it.occurredAtMs)
                }
            }
        } else {
            emptyList()
        }

        val nightEvents = if (SignalType.HEADLIGHT in supported && coordinate != null) {
            NightSegmentDetector(zone).detect(recording.headlights, startedAtMs, endedAtMs, coordinate).map {
                TripEvent.NightSegmentEvent(
                    id = newId(),
                    occurredAtMs = it.occurredAtMs,
                    durationSec = it.durationSec,
                    headlightOffSec = it.headlightOffSec,
                )
            }
        } else {
            emptyList()
        }

        val seatBeltEvents = if (SignalType.SEAT_BELT in supported && SignalType.SPEED in supported) {
            listOfNotNull(
                seatBeltJudge.judge(recording.seatBelts, recording.speeds, recording.vehicleStates, startedAtMs, endedAtMs)?.let {
                    TripEvent.SeatBeltEvent(
                        id = newId(),
                        occurredAtMs = it.occurredAtMs,
                        buckledAtDeparture = it.buckledAtDeparture,
                        unbuckledMovingSec = it.unbuckledMovingSec,
                    )
                },
            )
        } else {
            emptyList()
        }

        val safetyWarningEvents = if (SignalType.SAFETY_WARNING in supported && SignalType.SPEED in supported) {
            safetyWarningDetector.detect(recording.telltales, recording.speeds, startedAtMs, endedAtMs).map {
                TripEvent.SafetyWarningEvent(id = newId(), occurredAtMs = it.occurredAtMs)
            }
        } else {
            emptyList()
        }

        // 출발 시점 벨트 상태를 몰라 판정하지 못한 운행은 서버가 항목을 빼도록 supported에서 제외함
        val measuredSupported = if (SignalType.SEAT_BELT in supported && SignalType.SPEED in supported && seatBeltEvents.isEmpty()) {
            supported - SignalType.SEAT_BELT
        } else {
            supported
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
            supported = measuredSupported,
            events = (turnEvents + signalLeftOnEvents + harshEvents + nightEvents + seatBeltEvents + safetyWarningEvents).sortedBy { it.occurredAtMs },
        )
    }
}
