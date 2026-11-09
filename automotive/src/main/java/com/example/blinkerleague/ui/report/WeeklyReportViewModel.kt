package com.example.blinkerleague.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.domain.model.WeeklyMission
import com.example.blinkerleague.domain.model.WeeklyReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 주간 리포트. 처음에는 서버 기본(지난주)을 받고, 이전 주·다음 주로 넘김.
 * 다음 주로는 지난주까지만 감 (끝나지 않은 주는 서버가 400)
 */
class WeeklyReportViewModel(
    private val loadReport: suspend (week: String?) -> WeeklyReport?,
    private val clock: () -> Long,
    private val zone: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WeeklyReportUiState>(WeeklyReportUiState.Loading)
    val uiState: StateFlow<WeeklyReportUiState> = _uiState.asStateFlow()

    private var currentKey: String? = null

    /** 볼 수 있는 가장 최근 주 = 지난주 */
    private fun lastWeekKey(): String =
        IsoWeek.key(IsoWeek.mondayOf(Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()).minusWeeks(1))

    init {
        load(null)
    }

    fun previousWeek() = currentKey?.let { IsoWeek.shift(it, -1) }?.let(::load)

    fun nextWeek() {
        val next = currentKey?.let { IsoWeek.shift(it, 1) } ?: return
        if (next <= lastWeekKey()) load(next)
    }

    private fun load(week: String?) {
        _uiState.value = WeeklyReportUiState.Loading
        viewModelScope.launch {
            val report = loadReport(week)
            val key = report?.week ?: week ?: lastWeekKey()
            currentKey = key
            val canGoNext = key < lastWeekKey()
            _uiState.value = report?.toUiState(canGoNext) ?: WeeklyReportUiState.Unavailable(key, canGoNext)
        }
    }

    private fun WeeklyReport.toUiState(canGoNext: Boolean): WeeklyReportUiState.Loaded {
        val monday = IsoWeek.monday(week)
        return WeeklyReportUiState.Loaded(
            weekKey = week,
            range = monday?.let { "${DATE.format(it)} ~ ${DATE.format(it.plusDays(6))}" } ?: week,
            canGoNext = canGoNext,
            trips = trips,
            distanceKm = distanceKm,
            metrics = metrics.map { MetricRow(it.metric, it.thisWeek, it.change, it.metric == weakestMetric) },
            lastMission = lastMission?.toRow(),
            nextMission = nextMission?.toRow(),
        )
    }

    private fun WeeklyMission.toRow() = MissionRow(title, target, minDistanceKm, status)

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")
    }
}
