package com.example.blinkerleague.data.remote.http

import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.ConfigApi
import com.example.blinkerleague.data.remote.DriverApi
import com.example.blinkerleague.data.remote.LeagueApi
import com.example.blinkerleague.data.remote.MissionApi
import com.example.blinkerleague.data.remote.RewardApi
import com.example.blinkerleague.data.remote.TripApi
import com.example.blinkerleague.data.remote.dto.BadgeDto
import com.example.blinkerleague.data.remote.dto.LeagueResponse
import com.example.blinkerleague.data.remote.dto.LeagueVisibilityDto
import com.example.blinkerleague.data.remote.dto.LeagueVisibilityResponseDto
import com.example.blinkerleague.data.remote.dto.PointsResponse
import com.example.blinkerleague.data.remote.dto.RecentTripDto
import com.example.blinkerleague.data.remote.dto.RegisterDriverRequest
import com.example.blinkerleague.data.remote.dto.RegisterDriverResponse
import com.example.blinkerleague.data.remote.dto.StreakResponse
import com.example.blinkerleague.data.remote.dto.ThresholdsResponse
import com.example.blinkerleague.data.remote.dto.TodayMissionResponse
import com.example.blinkerleague.data.remote.dto.TripScoreResponse
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import com.example.blinkerleague.data.remote.dto.WeeklyReportResponse
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** 응답 본문을 읽을 수 없음 (형식 불일치). 다시 보내도 같으므로 재전송 대상이 아님 */
const val UNREADABLE_RESPONSE = -1

/** Retrofit 호출을 [ApiResult]로. HTTP 오류 → HttpError, 연결 실패 → NetworkError */
internal suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ApiResult.HttpError(e.code())
} catch (e: SerializationException) {
    ApiResult.HttpError(UNREADABLE_RESPONSE)
} catch (e: IOException) {
    ApiResult.NetworkError(e)
}

class HttpTripApi(private val service: BlinkerService) : TripApi {
    override suspend fun uploadTrip(request: TripUploadRequest): ApiResult<TripScoreResponse> =
        apiCall { service.uploadTrip(request) }

    override suspend fun getScore(tripId: String): ApiResult<TripScoreResponse> = apiCall { service.score(tripId) }

    override suspend fun getRecentTrips(limit: Int): ApiResult<List<RecentTripDto>> =
        apiCall { service.recentTrips(limit) }
}

class HttpMissionApi(private val service: BlinkerService) : MissionApi {
    override suspend fun todayMission(): ApiResult<TodayMissionResponse> = apiCall { service.todayMission() }
}

class HttpConfigApi(private val service: BlinkerService) : ConfigApi {
    override suspend fun thresholds(): ApiResult<ThresholdsResponse> = apiCall { service.thresholds() }
}

class HttpRewardApi(private val service: BlinkerService) : RewardApi {
    override suspend fun points(): ApiResult<PointsResponse> = apiCall { service.points() }
    override suspend fun streak(): ApiResult<StreakResponse> = apiCall { service.streak() }
    override suspend fun badges(): ApiResult<List<BadgeDto>> = apiCall { service.badges() }
    override suspend fun weeklyReport(week: String?): ApiResult<WeeklyReportResponse> = apiCall { service.weeklyReport(week) }
}

class HttpLeagueApi(private val service: BlinkerService) : LeagueApi {
    override suspend fun currentLeague(): ApiResult<LeagueResponse> = apiCall { service.currentLeague() }

    override suspend fun setVisibility(visible: Boolean): ApiResult<LeagueVisibilityResponseDto> =
        apiCall { service.setLeagueVisibility(LeagueVisibilityDto(visible)) }
}

class HttpDriverApi(private val service: BlinkerService) : DriverApi {
    override suspend fun register(request: RegisterDriverRequest): ApiResult<RegisterDriverResponse> =
        apiCall { service.register(request) }
}
