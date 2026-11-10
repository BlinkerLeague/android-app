package com.example.blinkerleague.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import com.example.blinkerleague.ui.theme.OnWarningContainer
import java.time.ZoneId

private val MetricDetailViewModelFactory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container
        val handle = createSavedStateHandle()
        val tripId = ResultViewModel.tripId(handle)
        MetricDetailViewModel(
            result = container.tripRepository.observeResult(tripId),
            item = MetricDetailViewModel.item(handle),
            loadServerDetails = { container.tripRepository.metricDetails(tripId) },
            grades = container.config.grades,
            tips = container.config.tips,
            zone = ZoneId.systemDefault(),
        )
    }
}

/** 항목 상세 (SCR-02b): 결과 화면 항목 행·타임라인 카드에서 들어옴 */
@Composable
fun MetricDetailScreen(onBack: () -> Unit, viewModel: MetricDetailViewModel = viewModel(factory = MetricDetailViewModelFactory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MetricDetailContent(state, onBack)
}

@Composable
fun MetricDetailContent(state: MetricDetailUiState, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        when (state) {
            MetricDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            MetricDetailUiState.NotFound -> Text(stringResource(R.string.result_not_found), style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
            is MetricDetailUiState.Loaded -> Loaded(state, onBack)
        }
    }
}

@Composable
private fun Loaded(state: MetricDetailUiState.Loaded, onBack: () -> Unit) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        DetailPanel(Modifier.weight(0.9f).fillMaxHeight()) {
            Text(stringResource(metricLabel(state.item)), style = MaterialTheme.typography.headlineMedium)
            val value = state.value
            if (value is MetricValue.Score) {
                Text(stringResource(R.string.detail_score, value.score), style = MaterialTheme.typography.displayMedium.copy(fontSize = 72.sp), color = MaterialTheme.colorScheme.primary)
            } else {
                Text(stringResource(metricValueText(value)), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.turnCounts?.let { c ->
                Text(stringResource(R.string.detail_counts, c.turns, c.onTime, c.late, c.missed), style = MaterialTheme.typography.bodyLarge)
                if (!c.fromServer) {
                    Text(stringResource(R.string.detail_counts_local), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.tip?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.detail_tip), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            Box(Modifier.weight(1f))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(R.string.plays_back), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (state.turns.isNotEmpty()) {
            DetailPanel(Modifier.weight(1.1f).fillMaxHeight()) {
                Text(stringResource(R.string.detail_turns), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn {
                    items(state.turns) { play ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(play.time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 20.dp))
                            Text(
                                stringResource(if (play.direction == TurnDirection.LEFT) R.string.summary_turn_left_short else R.string.summary_turn_right_short),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(if (play.goal) R.string.plays_signal_on else R.string.plays_signal_off),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (play.goal) OnSuccessContainer else OnWarningContainer,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

internal fun metricLabel(item: MetricItem): Int = when (item) {
    MetricItem.TURN_SIGNAL -> R.string.result_metric_turn_signal
    MetricItem.SMOOTH_DRIVING -> R.string.result_metric_smooth
    MetricItem.SAFETY_WARNING -> R.string.result_metric_safety
    MetricItem.NIGHT_HEADLIGHT -> R.string.result_metric_headlight
}

internal fun metricValueText(value: MetricValue): Int = when (value) {
    MetricValue.Pending -> R.string.result_metric_pending
    MetricValue.NoTurn -> R.string.result_metric_no_turn
    MetricValue.Unsupported -> R.string.result_metric_unsupported
    MetricValue.Excluded -> R.string.result_metric_excluded
    MetricValue.NotScored -> R.string.result_metric_not_scored
    MetricValue.ComingSoon -> R.string.result_metric_coming_soon
    is MetricValue.Score -> R.string.result_metric_pending
}

@Composable
private fun DetailPanel(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun MetricDetailPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        MetricDetailContent(
            MetricDetailUiState.Loaded(
                MetricItem.TURN_SIGNAL, MetricValue.Score(67), null, TurnCounts(3, 2, 0, 1, true),
                listOf(PlayCard("08:15", TurnDirection.LEFT, true), PlayCard("08:27", TurnDirection.RIGHT, false)),
                "회전 5초 전에 깜빡이를 켜 보세요.",
            ),
            onBack = {},
        )
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun MetricDetailNarrowPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        MetricDetailContent(
            MetricDetailUiState.Loaded(MetricItem.TURN_SIGNAL, MetricValue.NoTurn, null, TurnCounts(0, 0, 0, 0, false), emptyList(), null),
            onBack = {},
        )
    }
}
