package com.example.blinkerleague

import android.content.Context
import com.example.blinkerleague.data.auth.PrefsTokenStore
import com.example.blinkerleague.data.auth.TokenStore
import com.example.blinkerleague.data.config.ConfigRepository
import com.example.blinkerleague.data.config.PrefsConfigCache
import com.example.blinkerleague.data.local.AppDatabase
import com.example.blinkerleague.data.location.LocationSource
import com.example.blinkerleague.data.location.PleosLocationSource
import com.example.blinkerleague.data.permission.PermissionRepository
import com.example.blinkerleague.data.permission.RuntimePermission
import com.example.blinkerleague.data.remote.DriverApi
import com.example.blinkerleague.data.remote.FakeDriverApi
import com.example.blinkerleague.data.remote.FakeLeagueApi
import com.example.blinkerleague.data.remote.FakeMissionApi
import com.example.blinkerleague.data.remote.FakeRewardApi
import com.example.blinkerleague.data.remote.FakeTripApi
import com.example.blinkerleague.data.remote.LeagueApi
import com.example.blinkerleague.data.remote.MissionApi
import com.example.blinkerleague.data.remote.RewardApi
import com.example.blinkerleague.data.remote.TripApi
import com.example.blinkerleague.data.remote.http.HttpClientFactory
import com.example.blinkerleague.data.remote.http.HttpConfigApi
import com.example.blinkerleague.data.remote.http.HttpDriverApi
import com.example.blinkerleague.data.remote.http.HttpLeagueApi
import com.example.blinkerleague.data.remote.http.HttpMissionApi
import com.example.blinkerleague.data.remote.http.HttpRewardApi
import com.example.blinkerleague.data.remote.http.HttpTripApi
import com.example.blinkerleague.data.repository.DriverRepository
import com.example.blinkerleague.data.repository.LeagueRepository
import com.example.blinkerleague.data.repository.MissionRepository
import com.example.blinkerleague.data.repository.RewardRepository
import com.example.blinkerleague.data.repository.TripMapper
import com.example.blinkerleague.data.repository.TripRepository
import com.example.blinkerleague.data.vehicle.PleosVehicleSignalSource
import com.example.blinkerleague.data.vehicle.VehicleSignalSource
import com.example.blinkerleague.domain.config.Coordinate
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.service.RecordingStatus
import com.example.blinkerleague.service.TripVoiceGuide
import com.example.blinkerleague.voice.PleosTtsAnnouncer
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 앱 전체에서 하나만 쓰는 객체 모음 (수동 DI). DI 방식(Hilt/수동)이 정해지기 전까지 임시로 씀.
 */
class AppContainer(context: Context) {

    private val database = AppDatabase.create(context)

    /** 앱 전체 시간대. 기기 시간대는 여기서만 읽고 나머지는 주입받음 */
    val zone: ZoneId = ZoneId.systemDefault()

    /** 런타임 권한(속도·위치) 상태. 화면이 다시 보일 때 refresh */
    val permissions = PermissionRepository(context)

    private fun granted(permission: RuntimePermission) =
        permissions.status(permission).map { it == PermissionStatus.GRANTED }

    /** 실제 차량 신호 (Vehicle SDK + CarProperty). 처음 쓸 때 SDK에 연결함. 속도는 권한이 생기면 그때 구독 */
    val vehicleSignalSource: VehicleSignalSource by lazy {
        PleosVehicleSignalSource(context, speedGranted = granted(RuntimePermission.SPEED))
    }

    /** 실제 위치 (Pleos FusedLocation SDK). 권한이 생기면 그때 구독 */
    val locationSource: LocationSource by lazy {
        PleosLocationSource(context, locationGranted = granted(RuntimePermission.LOCATION))
    }

    /** 서버 주소가 있으면 실제 서버, 없으면 서버 규칙을 흉내 내는 Fake (앱을 끄면 초기화됨) */
    val usesServer: Boolean = BuildConfig.SERVER_URL.isNotBlank()

    // Fake 모드도 등록 정보를 저장해 앱을 다시 켜도 온보딩이 반복되지 않게 함 (파일은 서버 모드와 분리)
    private val tokens: TokenStore = PrefsTokenStore(context, fileName = if (usesServer) "auth" else "auth_fake")

    private val tripApi: TripApi
    private val missionApi: MissionApi
    private val driverApi: DriverApi
    private val rewardApi: RewardApi
    private val leagueApi: LeagueApi

    init {
        if (usesServer) {
            val service = HttpClientFactory.create(BuildConfig.SERVER_URL, tokens)
            tripApi = HttpTripApi(service)
            missionApi = HttpMissionApi(service)
            driverApi = HttpDriverApi(service)
            rewardApi = HttpRewardApi(service)
            leagueApi = HttpLeagueApi(service)
        } else {
            val fakeTrips = FakeTripApi()
            tripApi = fakeTrips
            val fakeMissions = FakeMissionApi(fakeTrips, System::currentTimeMillis, zone)
            missionApi = fakeMissions
            driverApi = FakeDriverApi()
            val fakeRewards = FakeRewardApi(fakeTrips, fakeMissions, System::currentTimeMillis, zone)
            fakeTrips.earnedPointsLookup = fakeRewards::earnedPointsFor
            rewardApi = fakeRewards
            leagueApi = FakeLeagueApi(fakeTrips, { tokens.profile()?.regionCode }, System::currentTimeMillis, zone) { config.league }
        }
    }

    /** 측정 기준값. 서버 모드면 앱 시작 때 받아 갱신 (version이 바뀐 경우만), 못 받으면 저장 값 → 기본값 */
    val configRepository = ConfigRepository(
        api = if (usesServer) HttpConfigApi(HttpClientFactory.create(BuildConfig.SERVER_URL, tokens)) else null,
        cache = PrefsConfigCache(context),
    )

    /** 지금 기준값. 운행 기록·화면은 시작할 때 이 값을 읽음 */
    val config: DetectionConfig get() = configRepository.config.value

    /** 등록 지역의 대표 좌표 (야간 판정용 일출 · 일몰 계산). 등록 전이거나 목록에 없는 지역이면 null */
    fun regionCoordinate(): Coordinate? = tokens.profile()?.regionCode?.let(config.night::representativeCoordinate)

    /** 2차 보상: 포인트·연속 달성·배지·주간 리포트 */
    val rewardRepository = RewardRepository(rewardApi)

    /** 지역 리그 (서버 4.4, 순위 공개 설정 포함) */
    val leagueRepository = LeagueRepository(leagueApi) { tokens.profile()?.nickname }

    /** 운전자 등록과 등록 여부 (온보딩) */
    val driverRepository = DriverRepository(driverApi, tokens)

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
        tripConfig = { config.trip },
        scope = applicationScope,
    )

    private val _recordingStatus = MutableStateFlow(RecordingStatus.OFF)

    /** 운행 수집 서비스 상태 (화면의 "기록이 꺼져 있어요" 표시용) */
    val recordingStatus: StateFlow<RecordingStatus> = _recordingStatus.asStateFlow()

    fun setRecordingStatus(status: RecordingStatus) {
        _recordingStatus.value = status
    }

    private val _guardBypass = MutableStateFlow(false)

    /** 주행 안전 가드 우회 (디버그 빌드 시연용, `DebugTools`에서만 켬). 릴리스에서는 항상 false */
    val guardBypass: StateFlow<Boolean> = _guardBypass.asStateFlow()

    internal fun setGuardBypass(enabled: Boolean) {
        _guardBypass.value = enabled
    }

    fun newId(): String = UUID.randomUUID().toString()
}
