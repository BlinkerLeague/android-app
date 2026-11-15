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
import com.example.blinkerleague.domain.model.WiperSample
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
    /** 와이퍼 상태 변화. 우천 구간 판정에 씀 */
    val wipers: List<WiperSample> = emptyList(),
)

/**
 * 끝난 운행의 시계열을 모아 [Trip]으로 만듦: 핸들 각도로 회전 감지 → 깜빡이 판정 → 이벤트, 거리 계산(속도 적분 우선).
 *
 * - 지원한다고 했어도 기록 샘플이 하나도 없는 브레이크 · 하향등 · 경고등 · 와이퍼 · 속도는 맨 앞에서 supported에서 빼고,
 *   아래 판정과 [Trip.supported]는 모두 줄인 집합을 씀 (운행 시작 전에 이어받은 샘플도 "있음"으로 봄)
 * - 방향지시등(TURN_SIGNAL)과 핸들 각도(STEERING_ANGLE)가 모두 지원돼야 회전 이벤트를 만듦 (아니면 서버에서 항목 제외)
 * - 거기에 속도(SPEED)까지 지원되면 깜빡이를 켠 채 잊은 이벤트도 만듦
 * - 속도(SPEED)와 브레이크(BRAKE)가 지원되면 급가속 · 급감속 이벤트를 만듦 (운행 구간 안의 것만)
 * - 하향등(HEADLIGHT)이 지원되고 대표 좌표가 있으면 야간 구간 이벤트를 만듦
 * - 안전벨트(SEAT_BELT)와 속도(SPEED)가 지원되면 운행당 안전벨트 이벤트 하나를 출발 시각에 만듦 (출발 시점 상태를 모르면 만들지 않고 supported에서 SEAT_BELT를 뺌)
 * - 안전 경고등(SAFETY_WARNING)과 속도(SPEED)가 지원되면 경고등이 한 번 켜진 주기마다 주행 중이 된 첫 시각에 안전 경고 이벤트를 만듦
 * - 와이퍼(WIPER)와 하향등(HEADLIGHT)이 지원되면 와이퍼가 기준 시간 넘게 켜진 구간마다 우천 구간 이벤트를 만듦
 * - 이벤트는 발생 시각 순으로 정렬함
 * - 이벤트가 `trip.maxEvents`를 넘으면 횟수형(급가속 · 급감속 · 안전 경고)을 늦은 것부터 빼고, 그래도 넘치면 이른 것부터 maxEvents개만 남김
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
    private val rainDetector = RainSegmentDetector(config.rain)
    private val maxEvents = config.trip.maxEvents

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
        // 지원한다고 했지만 기록 샘플이 하나도 없는 신호는 측정하지 못한 것으로 보고 이후 판정과 supported에서 뺌
        val recorded = mapOf(
            SignalType.BRAKE to recording.brakes,
            SignalType.HEADLIGHT to recording.headlights,
            SignalType.SAFETY_WARNING to recording.telltales,
            SignalType.WIPER to recording.wipers,
            SignalType.SPEED to recording.speeds,
        )
        val measurable = supported - recorded.filterValues { it.isEmpty() }.keys
        val window = startedAtMs..endedAtMs
        val locations = recording.locations.filter { it.timestampMs in window }

        val canMeasureTurns = SignalType.TURN_SIGNAL in measurable && SignalType.STEERING_ANGLE in measurable
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
        val signalLeftOnEvents = if (canMeasureTurns && SignalType.SPEED in measurable) {
            signalCancelDetector.detect(recording.signals, turns, recording.speeds, startedAtMs, endedAtMs).map {
                TripEvent.SignalLeftOnEvent(id = newId(), occurredAtMs = it.occurredAtMs, durationSec = it.durationSec)
            }
        } else {
            emptyList()
        }
        val harshEvents = if (SignalType.SPEED in measurable && SignalType.BRAKE in measurable) {
            harshDetector.detect(recording.speeds, recording.brakes).filter { it.occurredAtMs in window }.map {
                when (it.kind) {
                    HarshKind.ACCEL -> TripEvent.HarshAccelEvent(id = newId(), occurredAtMs = it.occurredAtMs)
                    HarshKind.BRAKE -> TripEvent.HarshBrakeEvent(id = newId(), occurredAtMs = it.occurredAtMs)
                }
            }
        } else {
            emptyList()
        }

        val nightEvents = if (SignalType.HEADLIGHT in measurable && coordinate != null) {
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

        val seatBeltEvents = if (SignalType.SEAT_BELT in measurable && SignalType.SPEED in measurable) {
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

        val safetyWarningEvents = if (SignalType.SAFETY_WARNING in measurable && SignalType.SPEED in measurable) {
            safetyWarningDetector.detect(recording.telltales, recording.speeds, startedAtMs, endedAtMs).map {
                TripEvent.SafetyWarningEvent(id = newId(), occurredAtMs = it.occurredAtMs)
            }
        } else {
            emptyList()
        }

        val rainEvents = if (SignalType.WIPER in measurable && SignalType.HEADLIGHT in measurable) {
            rainDetector.detect(recording.wipers, recording.headlights, startedAtMs, endedAtMs).map {
                TripEvent.RainSegmentEvent(
                    id = newId(),
                    occurredAtMs = it.occurredAtMs,
                    durationSec = it.durationSec,
                    headlightOffSec = it.headlightOffSec,
                    knownLimitSec = it.knownLimitSec,
                    overRainLimitSec = it.overRainLimitSec,
                )
            }
        } else {
            emptyList()
        }

        // 출발 시점 벨트 상태를 몰라 판정하지 못한 운행은 서버가 항목을 빼도록 supported에서 제외함
        val measuredSupported = if (SignalType.SEAT_BELT in measurable && SignalType.SPEED in measurable && seatBeltEvents.isEmpty()) {
            measurable - SignalType.SEAT_BELT
        } else {
            measurable
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
            events = capEvents(
                (turnEvents + signalLeftOnEvents + harshEvents + nightEvents + seatBeltEvents + safetyWarningEvents + rainEvents).sortedBy { it.occurredAtMs },
            ),
        )
    }

    /**
     * 이벤트가 [maxEvents]를 넘으면 횟수형 이벤트(급가속 · 급감속 · 안전 경고)를 늦은 것부터 뺌.
     * 그래도 넘치면 이른 것부터 [maxEvents]개만 남김. [events]는 시각 순
     */
    private fun capEvents(events: List<TripEvent>): List<TripEvent> {
        val excess = events.size - maxEvents
        if (excess <= 0) return events
        val dropped = events.filter { it.isCountType() }.takeLast(excess).toSet()
        return events.filterNot { it in dropped }.take(maxEvents)
    }

    private fun TripEvent.isCountType(): Boolean =
        this is TripEvent.HarshAccelEvent || this is TripEvent.HarshBrakeEvent || this is TripEvent.SafetyWarningEvent
}
