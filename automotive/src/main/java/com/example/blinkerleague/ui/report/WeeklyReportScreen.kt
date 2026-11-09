package com.example.blinkerleague.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.domain.model.WeeklyMissionStatus
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import com.example.blinkerleague.ui.theme.OnWarningContainer
import java.time.ZoneId

private val WeeklyReportViewModelFactory = viewModelFactory {
    initializer {
        val rewards = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container.rewardRepository
        WeeklyReportViewModel(rewards::weeklyReport, System::currentTimeMillis, ZoneId.systemDefault())
    }
}

@Composable
fun WeeklyReportScreen(onBack: () -> Unit, viewModel: WeeklyReportViewModel = viewModel(factory = WeeklyReportViewModelFactory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeeklyReportContent(state, viewModel::previousWeek, viewModel::nextWeek, onBack)
}

@Composable
fun WeeklyReportContent(state: WeeklyReportUiState, onPrevious: () -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val (title, canGoNext) = when (state) {
            is WeeklyReportUiState.Loaded -> state.range to state.canGoNext
            is WeeklyReportUiState.Unavailable -> (state.weekKey ?: "") to state.canGoNext
            WeeklyReportUiState.Loading -> "" to false
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onPrevious, enabled = state !is WeeklyReportUiState.Loading, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(R.string.report_previous), style = MaterialTheme.typography.labelLarge)
            }
            Text(
                stringResource(R.string.report_title, title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(R.string.report_next), style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(R.string.report_back), style = MaterialTheme.typography.labelLarge)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                WeeklyReportUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is WeeklyReportUiState.Unavailable -> Text(
                    stringResource(R.string.report_unavailable),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
                is WeeklyReportUiState.Loaded -> Loaded(state)
            }
        }
    }
}

@Composable
private fun Loaded(state: WeeklyReportUiState.Loaded) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(0.9f).fillMaxHeight()) {
            Label(R.string.report_summary)
            Text(
                if (state.trips == 0) stringResource(R.string.report_no_trips) else stringResource(R.string.report_trips, state.trips, state.distanceKm),
                style = MaterialTheme.typography.titleLarge,
            )
            state.lastMission?.let { m ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Label(R.string.report_last_mission)
                Text(m.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        when (m.status) {
                            WeeklyMissionStatus.ACHIEVED -> R.string.report_mission_achieved
                            WeeklyMissionStatus.FAILED -> R.string.report_mission_failed
                            WeeklyMissionStatus.ASSIGNED -> R.string.report_mission_assigned
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (m.status == WeeklyMissionStatus.ACHIEVED) OnSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.nextMission?.let { m ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Label(R.string.report_next_mission)
                Text(m.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                // 서버 D7: 맞춤 미션은 자동 등록, 수락 버튼 없음
                Text(
                    listOfNotNull(
                        stringResource(R.string.report_next_registered),
                        m.minDistanceKm?.let { stringResource(R.string.report_mission_min_km, it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Panel(Modifier.weight(1.1f).fillMaxHeight()) {
            Label(R.string.report_metrics)
            if (state.metrics.isEmpty()) {
                Text(stringResource(R.string.report_metrics_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                state.metrics.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MetricLine(row)
                }
            }
        }
    }
}

@Composable
private fun MetricLine(row: MetricRow) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(metricName(row.metric), style = MaterialTheme.typography.bodyLarge)
            if (row.weakest) {
                Text(stringResource(R.string.report_weakest), style = MaterialTheme.typography.bodyMedium, color = OnWarningContainer)
            }
        }
        Text(
            row.thisWeek?.toString() ?: stringResource(R.string.report_no_record),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            when {
                row.change == null -> "—"
                row.change > 0 -> stringResource(R.string.report_up, row.change)
                row.change < 0 -> stringResource(R.string.report_down, -row.change)
                else -> stringResource(R.string.report_same)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                row.change == null || row.change == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                row.change > 0 -> OnSuccessContainer
                else -> OnWarningContainer
            },
            modifier = Modifier.padding(start = 16.dp).heightIn(min = 24.dp),
            textAlign = TextAlign.End,
        )
    }
}

/** 서버 점수 항목 이름 → 화면 이름 (모르는 이름은 그대로) */
@Composable
private fun metricName(metric: String): String = when (metric) {
    "TURN_SIGNAL" -> stringResource(R.string.result_metric_turn_signal)
    "SMOOTH_DRIVING" -> stringResource(R.string.result_metric_smooth)
    "SAFETY_WARNING" -> stringResource(R.string.result_metric_safety)
    "NIGHT_HEADLIGHT" -> stringResource(R.string.result_metric_headlight)
    "SIGNAL_CANCEL" -> stringResource(R.string.metric_signal_cancel)
    "TURN_SPEED" -> stringResource(R.string.metric_turn_speed)
    "SEAT_BELT" -> stringResource(R.string.metric_seat_belt)
    "RAIN_DRIVING" -> stringResource(R.string.metric_rain_driving)
    else -> metric
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun WeeklyReportPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        WeeklyReportContent(
            WeeklyReportUiState.Loaded(
                "2026-W45", "11.02 ~ 11.08", true, 5, 42.3,
                listOf(MetricRow("TURN_SIGNAL", 75, 5, true), MetricRow("SMOOTH_DRIVING", 88, -2, false)),
                MissionRow("회전 깜빡이 주간 90점", 90, 30.0, WeeklyMissionStatus.FAILED),
                MissionRow("회전 깜빡이 주간 90점", 90, 30.0, WeeklyMissionStatus.ASSIGNED),
            ),
            {}, {}, {},
        )
    }
}
