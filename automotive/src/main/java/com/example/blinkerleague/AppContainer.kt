package com.example.blinkerleague

import android.content.Context
import com.example.blinkerleague.data.local.AppDatabase
import com.example.blinkerleague.data.location.LocationSource
import com.example.blinkerleague.data.location.PleosLocationSource
import com.example.blinkerleague.data.remote.FakeMissionApi
import com.example.blinkerleague.data.remote.FakeTripApi
import com.example.blinkerleague.data.repository.MissionRepository
import com.example.blinkerleague.data.repository.TripMapper
import com.example.blinkerleague.data.repository.TripRepository
import com.example.blinkerleague.data.vehicle.PleosVehicleSignalSource
import com.example.blinkerleague.data.vehicle.VehicleSignalSource
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.service.RecordingStatus
import com.example.blinkerleague.service.TripVoiceGuide
import com.example.blinkerleague.voice.PleosTtsAnnouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.UUID

/**
 * 앱 전체에서 하나만 쓰는 객체 모음 (수동 DI). DI 방식(Hilt/수동)이 정해지기 전까지 임시로 씀.
 */
class AppContainer(context: Context) {
    val config: DetectionConfig = DetectionConfig.DEFAULT

    private val database = AppDatabase.create(context)

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 실제 차량 신호 (Vehicle SDK + CarProperty). 처음 쓸 때 SDK에 연결함 */
    val vehicleSignalSource: VehicleSignalSource by lazy { PleosVehicleSignalSource(context) }

    /** 실제 위치 (Pleos FusedLocation SDK) */
    val locationSource: LocationSource by lazy { PleosLocationSource(context) }

    // HTTP 구현체는 API 클라이언트 결정 후 교체. 그 전까지 서버 규칙을 흉내 내는 Fake를 씀 (앱을 끄면 초기화됨)
    private val tripApi = FakeTripApi()
    private val missionApi = FakeMissionApi(tripApi, System::currentTimeMillis, zone)

    val tripRepository = TripRepository(
        dao = database.tripDao(),
        api = tripApi,
        mapper = TripMapper(zone),
        clock = System::currentTimeMillis,
    )

    val missionRepository = MissionRepository(missionApi)

    /** 화면·기록 작업보다 오래 사는 작업용 (음성 안내 등) */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val voiceGuide = TripVoiceGuide(
        announcer = PleosTtsAnnouncer(context),
        todayMission = missionRepository::todayMission,
        tripResult = { tripId -> tripRepository.observeResult(tripId).first() },
        tripConfig = config.trip,
        scope = applicationScope,
    )

    private val _recordingStatus = MutableStateFlow(RecordingStatus.OFF)

    /** 운행 수집 서비스 상태 (화면의 "기록이 꺼져 있어요" 표시용) */
    val recordingStatus: StateFlow<RecordingStatus> = _recordingStatus.asStateFlow()

    fun setRecordingStatus(status: RecordingStatus) {
        _recordingStatus.value = status
    }

    fun newId(): String = UUID.randomUUID().toString()
}
