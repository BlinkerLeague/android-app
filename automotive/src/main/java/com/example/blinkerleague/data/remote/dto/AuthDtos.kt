package com.example.blinkerleague.data.remote.dto

import kotlinx.serialization.Serializable

// 서버 DriverController·AuthController 형식 (서버 plan.md 3.2, 8장)

/** `POST /api/drivers`. 닉네임 2~12자, 지역 코드는 시군구 5자리 숫자 */
@Serializable
data class RegisterDriverRequest(
    val nickname: String,
    val regionCode: String,
)

@Serializable
data class RegisterDriverResponse(
    val driverId: Long,
    val nickname: String,
    val regionCode: String,
    val accessToken: String,
    val refreshToken: String,
)

/** `POST /api/auth/refresh`. 쓴 Refresh 토큰은 무효가 되고 새 쌍을 받음 */
@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)
