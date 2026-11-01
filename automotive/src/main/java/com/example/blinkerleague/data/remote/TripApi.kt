package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.TripScoreResponse
import com.example.blinkerleague.data.remote.dto.TripUploadRequest

/**
 * 운행 업로드 API. HTTP 구현체는 API 클라이언트(Retrofit/Ktor) 결정 후 추가하고,
 * 그 전까지는 [FakeTripApi]로 개발함.
 */
interface TripApi {
    /** `POST /api/trips`. 같은 tripId를 다시 보내면 서버는 기존 결과를 돌려줌 (멱등) */
    suspend fun uploadTrip(request: TripUploadRequest): ApiResult<TripScoreResponse>
}
