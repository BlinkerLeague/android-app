package com.example.blinkerleague.domain.model

enum class MissionStatus { IN_PROGRESS, ACHIEVED, FAILED }

/** 오늘의 미션 (MVP: "회전 깜빡이 완벽" 1종) */
data class TodayMission(
    val title: String,
    val status: MissionStatus,
    val turns: Int,
    val successTurns: Int,
    val requiredTurns: Int,
)
