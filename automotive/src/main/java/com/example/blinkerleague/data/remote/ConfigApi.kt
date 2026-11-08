package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.ThresholdsResponse

/** 기준값 API. 인증 없이 부름 (서버 공개 API) */
interface ConfigApi {
    /** `GET /api/config/thresholds` */
    suspend fun thresholds(): ApiResult<ThresholdsResponse>
}
