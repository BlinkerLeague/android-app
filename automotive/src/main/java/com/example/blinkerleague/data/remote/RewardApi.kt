package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.BadgeDto
import com.example.blinkerleague.data.remote.dto.PointsResponse
import com.example.blinkerleague.data.remote.dto.StreakResponse
import com.example.blinkerleague.data.remote.dto.WeeklyReportResponse

/** 2차 보상 API: 포인트·연속 달성·배지·주간 리포트 (서버 plan.md 4.2·4.3·4.5) */
interface RewardApi {
    /** `GET /api/points`: 잔액, 오늘 적립·일일 상한, 최근 원장 */
    suspend fun points(): ApiResult<PointsResponse>

    /** `GET /api/streak` */
    suspend fun streak(): ApiResult<StreakResponse>

    /** `GET /api/badges`: 전체 배지 진열장 (잠금 포함, 진열 순서) */
    suspend fun badges(): ApiResult<List<BadgeDto>>

    /** `GET /api/reports/weekly?week=2026-W45`. week가 null이면 서버 기본(지난주) */
    suspend fun weeklyReport(week: String? = null): ApiResult<WeeklyReportResponse>
}
