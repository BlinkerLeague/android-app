package com.example.blinkerleague.data.remote

sealed interface ApiResult<out T> {
    data class Success<T>(val body: T) : ApiResult<T>

    /** 서버가 응답했지만 실패 코드 */
    data class HttpError(val code: Int) : ApiResult<Nothing>

    /** 연결 실패, 타임아웃 등 응답을 받지 못함 */
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>
}
