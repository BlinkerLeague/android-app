package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.auth.TokenStore
import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.DriverApi
import com.example.blinkerleague.data.remote.dto.RegisterDriverRequest
import kotlinx.coroutines.flow.StateFlow

/** 운전자 등록 (온보딩 SCR-00 "리그 입장"). 성공하면 토큰을 저장함 */
class DriverRepository(
    private val api: DriverApi,
    private val tokens: TokenStore,
) {
    /** 등록돼 있으면 홈, 아니면 온보딩 (screen-plan 4.1) */
    val registered: StateFlow<Boolean> = tokens.registered

    suspend fun register(nickname: String, regionCode: String): RegisterResult =
        when (val result = api.register(RegisterDriverRequest(nickname.trim(), regionCode))) {
            is ApiResult.Success -> {
                tokens.saveRegistration(result.body)
                RegisterResult.Registered
            }
            is ApiResult.HttpError -> when (result.code) {
                409 -> RegisterResult.NicknameTaken
                400 -> RegisterResult.Invalid
                else -> RegisterResult.Failed
            }
            is ApiResult.NetworkError -> RegisterResult.Offline
        }
}

/** 온보딩 화면 분기용 (`nicknameError` 등) */
enum class RegisterResult { Registered, NicknameTaken, Invalid, Offline, Failed }
