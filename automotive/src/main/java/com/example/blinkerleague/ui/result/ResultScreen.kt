package com.example.blinkerleague.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import com.example.blinkerleague.ui.theme.OnWarningContainer

private val ResultViewModelFactory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container
        val tripId = ResultViewModel.tripId(createSavedStateHandle())
        ResultViewModel(
            result = container.tripRepository.observeResult(tripId),
            sync = {
                container.tripRepository.uploadPending()
                container.tripRepository.refreshScore(tripId)
            },
            loadMission = container.missionRepository::todayMission,
            loadEarnedPoints = { container.tripRepository.earnedPoints(tripId) },
            grades = container.config.grades,
        )
    }
}

@Composable
fun ResultScreen(
    onOpenPlays: () -> Unit,
    onOpenMetric: (MetricItem) -> Unit,
    onOpenQuest: () -> Unit,
    onDone: () -> Unit,
    viewModel: ResultViewModel = viewModel(factory = ResultViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResultContent(state, onOpenPlays, onOpenQuest, onDone, onOpenMetric)
}

@Composable
fun ResultContent(
    state: ResultUiState,
    onOpenPlays: () -> Unit,
    onOpenQuest: () -> Unit,
    onDone: () -> Unit,
    onOpenMetric: (MetricItem) -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        when (state) {
            ResultUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ResultUiState.NotFound -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(stringResource(R.string.result_not_found), style = MaterialTheme.typography.titleLarge)
                DoneButton(onDone, Modifier.width(240.dp))
            }
            is ResultUiState.Loaded -> Loaded(state, onOpenPlays, onOpenQuest, onDone, onOpenMetric)
        }
    }
}

@Composable
private fun Loaded(
    state: ResultUiState.Loaded,
    onOpenPlays: () -> Unit,
    onOpenQuest: () -> Unit,
    onDone: () -> Unit,
    onOpenMetric: (MetricItem) -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(0.8f).fillMaxHeight()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Text(
                    stringResource(R.string.result_header, state.distanceKm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Headline(state)
            }
        }
        Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Panel(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.result_metrics_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.metrics.forEach { MetricLine(it, onClick = { onOpenMetric(it.item) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                state.mission?.let { QuestChip(it, onOpenQuest, Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onOpenPlays, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.result_open_plays), style = MaterialTheme.typography.labelLarge)
                }
                DoneButton(onDone)
            }
        }
    }
}

/** 핵심 숫자는 하나(평점). 점수가 없으면 상태 문구 */
@Composable
private fun Headline(state: ResultUiState.Loaded) {
    when (state.scoreState) {
        ScoreState.SCORED -> {
            Text(
                text = state.grade ?: "—",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 96.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            state.score?.let { Text(stringResource(R.string.result_score, it), style = MaterialTheme.typography.headlineMedium) }
            Goals(state)
            state.earnedPoints?.let { points ->
                Text(
                    if (points > 0) stringResource(R.string.result_points, points) else stringResource(R.string.result_points_capped),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (points > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        ScoreState.PENDING -> {
            Message(R.string.result_pending, R.string.result_pending_body)
            Goals(state)
        }
        ScoreState.NO_TURN -> Message(R.string.result_no_turn, R.string.result_no_turn_body)
        ScoreState.EXCLUDED_SHORT -> Message(R.string.result_excluded_short, null)
        ScoreState.REJECTED -> {
            Message(R.string.result_rejected, null)
            Goals(state)
        }
    }
}

@Composable
private fun Goals(state: ResultUiState.Loaded) {
    if (state.goals + state.conceded > 0) {
        Text(
            stringResource(R.string.result_goals, state.goals, state.conceded),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Message(title: Int, body: Int?) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    body?.let {
        Text(
            stringResource(it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MetricLine(row: MetricRow, onClick: () -> Unit) {
    val name = stringResource(
        when (row.item) {
            MetricItem.TURN_SIGNAL -> R.string.result_metric_turn_signal
            MetricItem.SMOOTH_DRIVING -> R.string.result_metric_smooth
            MetricItem.SAFETY_WARNING -> R.string.result_metric_safety
            MetricItem.NIGHT_HEADLIGHT -> R.string.result_metric_headlight
        },
    )
    val value = row.value
    val muted = value !is MetricValue.Score
    val textColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, color = textColor, modifier = Modifier.width(200.dp))
        if (value is MetricValue.Score) {
            val track = MaterialTheme.colorScheme.outlineVariant
            val fill = if (value.score >= 70) OnSuccessContainer else OnWarningContainer
            Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(track)) {
                Box(Modifier.fillMaxWidth(value.score.coerceIn(0, 100) / 100f).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(fill))
            }
            Text(value.score.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
        } else {
            Text(
                stringResource(
                    when (value) {
                        MetricValue.Pending -> R.string.result_metric_pending
                        MetricValue.NoTurn -> R.string.result_metric_no_turn
                        MetricValue.Unsupported -> R.string.result_metric_unsupported
                        MetricValue.Excluded -> R.string.result_metric_excluded
                        MetricValue.NotScored -> R.string.result_metric_not_scored
                        else -> R.string.result_metric_coming_soon
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            Text("—", color = textColor, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun QuestChip(mission: TodayMission, onOpenQuest: () -> Unit, modifier: Modifier) {
    val status = stringResource(
        when (mission.status) {
            MissionStatus.IN_PROGRESS -> R.string.mission_status_in_progress
            MissionStatus.ACHIEVED -> R.string.mission_status_achieved
            MissionStatus.FAILED -> R.string.mission_status_failed
        },
    )
    Surface(
        modifier = modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenQuest),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            Text("${stringResource(R.string.result_quest, mission.title)} · $status", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun DoneButton(onDone: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onDone, modifier = modifier.heightIn(min = 64.dp)) {
        Text(stringResource(R.string.result_done), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Box(Modifier.padding(24.dp)) { content() }
    }
}

private fun previewState(
    state: ScoreState,
    score: Int?,
    grade: String?,
    turnValue: MetricValue = if (score != null) MetricValue.Score(score) else MetricValue.Pending,
) = ResultUiState.Loaded(
    scoreState = state, score = score, grade = grade, distanceKm = 14.2, goals = 2, conceded = 1,
    metrics = listOf(
        MetricRow(MetricItem.TURN_SIGNAL, turnValue),
        MetricRow(MetricItem.SMOOTH_DRIVING, MetricValue.ComingSoon),
        MetricRow(MetricItem.SAFETY_WARNING, MetricValue.ComingSoon),
        MetricRow(MetricItem.NIGHT_HEADLIGHT, MetricValue.ComingSoon),
    ),
    mission = TodayMission("회전 깜빡이 완벽", MissionStatus.FAILED, 3, 2, 3),
)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ResultScoredPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) { ResultContent(previewState(ScoreState.SCORED, 67, "C"), {}, {}, {}) }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun ResultPendingNarrowPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) { ResultContent(previewState(ScoreState.PENDING, null, null), {}, {}, {}) }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ResultNoTurnPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        ResultContent(previewState(ScoreState.NO_TURN, null, null, MetricValue.NoTurn).copy(goals = 0, conceded = 0), {}, {}, {})
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ResultExcludedShortPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        ResultContent(previewState(ScoreState.EXCLUDED_SHORT, null, null, MetricValue.Excluded).copy(distanceKm = 0.6, goals = 0, conceded = 0), {}, {}, {})
    }
}
