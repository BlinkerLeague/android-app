package com.example.blinkerleague.data.remote.dto

import kotlinx.serialization.Serializable

// GET /api/missions/today 형식. 서버 plan.md 3.6 기준

@Serializable
data class TodayMissionResponse(
    val missionId: Long,
    val title: String,
    /** 예: 2026-11-03 (Asia/Seoul 기준 날짜) */
    val periodKey: String,
    /** `IN_PROGRESS`, `ACHIEVED`, `FAILED` */
    val status: String,
    val progress: MissionProgressDto,
)

@Serializable
data class MissionProgressDto(
    val turns: Int,
    val successTurns: Int,
    val requiredTurns: Int,
)
