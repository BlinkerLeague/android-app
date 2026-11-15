package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.TodayMissionResponse

/** 미션 API. HTTP 구현체는 API 클라이언트 결정 후 추가하고, 그 전까지는 [FakeMissionApi]로 개발함. */
interface MissionApi {
    /** `GET /api/missions/today` */
    suspend fun todayMission(): ApiResult<TodayMissionResponse>
}
