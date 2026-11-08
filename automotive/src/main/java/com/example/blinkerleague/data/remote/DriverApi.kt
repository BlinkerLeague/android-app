package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.RegisterDriverRequest
import com.example.blinkerleague.data.remote.dto.RegisterDriverResponse

/** 운전자 등록 API. 로그인 없이 등록 때 토큰을 받음 (서버 plan.md 3.2) */
interface DriverApi {
    /** `POST /api/drivers`. 닉네임 중복은 409, 입력 오류는 400 */
    suspend fun register(request: RegisterDriverRequest): ApiResult<RegisterDriverResponse>
}

/** 서버 규칙을 흉내 내는 개발·시연용 등록 API */
class FakeDriverApi : DriverApi {
    private val nicknames = mutableSetOf<String>()

    override suspend fun register(request: RegisterDriverRequest): ApiResult<RegisterDriverResponse> {
        if (request.nickname.length !in 2..12 || !request.regionCode.matches(Regex("\\d{5}"))) return ApiResult.HttpError(400)
        if (!nicknames.add(request.nickname)) return ApiResult.HttpError(409)
        val id = nicknames.size.toLong()
        return ApiResult.Success(
            RegisterDriverResponse(id, request.nickname, request.regionCode, "fake-access-$id", "fake-refresh-$id"),
        )
    }
}
