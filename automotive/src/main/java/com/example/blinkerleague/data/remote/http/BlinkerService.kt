package com.example.blinkerleague.data.remote.http

import com.example.blinkerleague.data.remote.dto.RecentTripDto
import com.example.blinkerleague.data.remote.dto.RegisterDriverRequest
import com.example.blinkerleague.data.remote.dto.RegisterDriverResponse
import com.example.blinkerleague.data.remote.dto.ThresholdsResponse
import com.example.blinkerleague.data.remote.dto.TodayMissionResponse
import com.example.blinkerleague.data.remote.dto.TripScoreResponse
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** 서버 API (Retrofit). 경로는 base URL 기준 상대 경로. 형식은 서버 plan.md 3장 */
interface BlinkerService {
    @POST("api/drivers")
    suspend fun register(@Body request: RegisterDriverRequest): RegisterDriverResponse

    @POST("api/trips")
    suspend fun uploadTrip(@Body request: TripUploadRequest): TripScoreResponse

    @GET("api/trips/{tripId}/score")
    suspend fun score(@Path("tripId") tripId: String): TripScoreResponse

    @GET("api/trips")
    suspend fun recentTrips(@Query("limit") limit: Int): List<RecentTripDto>

    @GET("api/config/thresholds")
    suspend fun thresholds(): ThresholdsResponse

    @GET("api/missions/today")
    suspend fun todayMission(): TodayMissionResponse
}
