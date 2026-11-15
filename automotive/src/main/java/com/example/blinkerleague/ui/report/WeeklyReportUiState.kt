package com.example.blinkerleague.ui.report

import com.example.blinkerleague.domain.model.WeeklyMissionStatus

/** 주간 리포트 (SCR-07) */
sealed interface WeeklyReportUiState {
    data object Loading : WeeklyReportUiState

    data class Unavailable(val weekKey: String?, val canGoNext: Boolean) : WeeklyReportUiState

    data class Loaded(
        val weekKey: String,
        /** 예: "11.02 ~ 11.08" */
        val range: String,
        val canGoNext: Boolean,
        val trips: Int,
        val distanceKm: Double,
        val metrics: List<MetricRow>,
        val lastMission: MissionRow?,
        val nextMission: MissionRow?,
    ) : WeeklyReportUiState
}

data class MetricRow(
    /** 서버 항목 이름 (TURN_SIGNAL 등). 화면에서 이름으로 바꿈 */
    val metric: String,
    val thisWeek: Int?,
    val change: Int?,
    /** 가장 약한 항목 표시 */
    val weakest: Boolean,
)

data class MissionRow(
    val title: String,
    val target: Int?,
    val minDistanceKm: Double?,
    val status: WeeklyMissionStatus,
)
