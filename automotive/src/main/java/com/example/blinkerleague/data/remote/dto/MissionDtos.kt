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
    /** 달성 보상 포인트 (SCR-03). 서버가 안 주면 null */
    val rewardPoint: Int? = null,
    /** 조건별 진행도 (SCR-01 진행 링, SCR-03) */
    val conditions: List<MissionConditionDto> = emptyList(),
)

@Serializable
data class MissionConditionDto(
    /** `TURN_COUNT`, `SIGNAL_RATE` */
    val type: String,
    val current: Int,
    val target: Int,
)

@Serializable
data class MissionProgressDto(
    val turns: Int,
    val successTurns: Int,
    val requiredTurns: Int,
)
