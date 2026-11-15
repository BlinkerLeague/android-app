package com.example.blinkerleague.service

import com.example.blinkerleague.data.location.LocationSource
import com.example.blinkerleague.data.repository.TripRepository
import com.example.blinkerleague.data.vehicle.VehicleSignalSource
import com.example.blinkerleague.domain.config.Coordinate
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.detection.TripAssembler
import com.example.blinkerleague.domain.detection.TripBoundary
import com.example.blinkerleague.domain.detection.TripRecording
import com.example.blinkerleague.domain.detection.TripSegmenter
import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.VehicleStateSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.ZoneId
import java.time.ZoneOffset

sealed interface RecorderState {
    /** 운행 전 (시동 대기) */
    data object Idle : RecorderState

    /** 운행 기록 중. 주행 안전 원칙상 이 상태에서는 점수를 보여주지 않음 */
    data class Recording(val tripId: String, val startedAtMs: Long) : RecorderState

    /** 운행이 끝나 로컬에 저장됨(업로드 대기열에 들어감). 업로드는 따로 트리거함 */
    data class Finished(val tripId: String) : RecorderState
}

/**
 * 신호·위치를 모아 운행 단위로 기록하는 흐름: 공급원 → [TripSegmenter] → 운행 끝나면 [TripAssembler] → 로컬 저장.
 * 업로드는 기록과 수명이 달라서 여기서 하지 않음 ([TripRepository.uploadPending]을 결과 화면·WorkManager에서 호출).
 *
 * Android Service와 분리된 일반 클래스라서 Fake 공급원으로 테스트·시연할 수 있음.
 * 백그라운드 수집 방식(Foreground Service 등)은 스파이크 2번 결과에 따라 이 클래스를 감싸서 만듦.
 *
 * @param clock P단 3분 경과를 확인할 현재 시각. 공급원 타임스탬프와 같은 시간 기준이어야 함
 * @param coordinate 등록 지역의 대표 좌표 (야간 판정용). 운행이 끝날 때 읽음. 모르면 null
 * @param zone 일출 · 일몰 날짜를 정하는 시간대
 */
class TripRecorder(
    private val vehicle: VehicleSignalSource,
    private val location: LocationSource,
    private val repository: TripRepository,
    private val config: DetectionConfig,
    private val clock: () -> Long,
    private val newId: () -> String,
    private val tickIntervalMs: Long = 1_000L,
    private val coordinate: () -> Coordinate? = { null },
    private val zone: ZoneId = ZoneOffset.UTC,
) {
    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private sealed interface Input {
        data class Vehicle(val sample: VehicleStateSample) : Input
        data class Steering(val sample: SteeringSample) : Input
        data class Signal(val sample: SignalSample) : Input
        data class Location(val sample: LocationSample) : Input
        data class Speed(val sample: SpeedSample) : Input
        data class Brake(val sample: BrakeSample) : Input
        data class Tick(val nowMs: Long) : Input
    }

    private val segmenter = TripSegmenter(config.trip)
    private val assembler = TripAssembler(config, newId)

    private var tripId: String? = null
    private var startedAtMs = 0L
    private val steering = mutableListOf<SteeringSample>()
    private val signals = mutableListOf<SignalSample>()
    private val vehicleStates = mutableListOf<VehicleStateSample>()
    private val locations = mutableListOf<LocationSample>()
    private val speeds = mutableListOf<SpeedSample>()
    private val brakes = mutableListOf<BrakeSample>()
    private var lastSpeed: SpeedSample? = null
    private var lastSignal: SignalSample? = null
    private var lastBrake: BrakeSample? = null
    private var lastVehicleState: VehicleStateSample? = null

    /** 취소될 때까지 수집함. 입력은 한 줄로 합쳐 순서대로 처리하므로 상태를 잠글 필요가 없음 */
    suspend fun run() {
        merge(
            vehicle.vehicleStates.map { Input.Vehicle(it) },
            vehicle.steeringAngles.map { Input.Steering(it) },
            vehicle.turnSignals.map { Input.Signal(it) },
            location.locations.map { Input.Location(it) },
            vehicle.speeds.map { Input.Speed(it) },
            vehicle.brakes.map { Input.Brake(it) },
            ticks(),
        ).collect { handle(it) }
    }

    private fun ticks(): Flow<Input> = flow {
        while (true) {
            delay(tickIntervalMs)
            emit(Input.Tick(clock()))
        }
    }

    private suspend fun handle(input: Input) {
        when (input) {
            is Input.Vehicle -> {
                lastVehicleState = input.sample
                if (tripId != null) vehicleStates += input.sample
                segmenter.onVehicleState(input.sample)?.let { onBoundary(it) }
            }
            is Input.Steering -> if (tripId != null) steering += input.sample
            is Input.Signal -> {
                lastSignal = input.sample
                if (tripId != null) signals += input.sample
            }
            is Input.Location -> if (tripId != null) locations += input.sample
            is Input.Speed -> {
                lastSpeed = input.sample
                if (tripId != null) speeds += input.sample
            }
            is Input.Brake -> {
                lastBrake = input.sample
                if (tripId != null) brakes += input.sample
            }
            is Input.Tick -> segmenter.onTick(input.nowMs)?.let { onBoundary(it) }
        }
    }

    private suspend fun onBoundary(boundary: TripBoundary) {
        when (boundary) {
            is TripBoundary.Started -> start(boundary.atMs)
            is TripBoundary.Ended -> finish(boundary.atMs)
        }
    }

    private fun start(atMs: Long) {
        val id = newId()
        tripId = id
        startedAtMs = atMs
        listOf(steering, signals, vehicleStates, locations, speeds, brakes).forEach { it.clear() }
        // 운행 시작 전부터 이어지던 상태(켜져 있던 깜빡이, 기어, 밟고 있던 브레이크)를 이어받음
        lastSignal?.let { signals += it }
        lastBrake?.let { brakes += it }
        lastVehicleState?.let { vehicleStates += it }
        lastSpeed?.let { speeds += it.copy(timestampMs = maxOf(it.timestampMs, atMs)) }
        _state.value = RecorderState.Recording(id, atMs)
    }

    private suspend fun finish(atMs: Long) {
        val id = tripId ?: return
        tripId = null
        val trip = assembler.assemble(
            tripId = id,
            startedAtMs = startedAtMs,
            endedAtMs = maxOf(atMs, startedAtMs),
            recording = TripRecording(
                steering.toList(), signals.toList(), vehicleStates.toList(), locations.toList(), speeds.toList(), brakes.toList(),
            ),
            supported = vehicle.supportedSignals(),
        )
        repository.saveFinishedTrip(trip)
        _state.value = RecorderState.Finished(id)
    }
}
