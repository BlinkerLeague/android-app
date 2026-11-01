package com.example.blinkerleague

import android.content.Context
import com.example.blinkerleague.data.local.AppDatabase
import com.example.blinkerleague.data.remote.FakeMissionApi
import com.example.blinkerleague.data.remote.FakeTripApi
import com.example.blinkerleague.data.repository.MissionRepository
import com.example.blinkerleague.data.repository.TripMapper
import com.example.blinkerleague.data.repository.TripRepository
import com.example.blinkerleague.domain.config.DetectionConfig
import java.time.ZoneId
import java.util.UUID

/**
 * 앱 전체에서 하나만 쓰는 객체 모음 (수동 DI). DI 방식(Hilt/수동)이 정해지기 전까지 임시로 씀.
 */
class AppContainer(context: Context) {
    val config: DetectionConfig = DetectionConfig.DEFAULT

    private val database = AppDatabase.create(context)

    private val zone: ZoneId = ZoneId.systemDefault()

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

    fun newId(): String = UUID.randomUUID().toString()
}
