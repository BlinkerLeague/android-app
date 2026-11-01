package com.example.blinkerleague.ui.summary

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import com.example.blinkerleague.ui.theme.OnWarningContainer
import com.example.blinkerleague.ui.theme.SuccessContainer
import com.example.blinkerleague.ui.theme.WarningContainer

private val SummaryViewModelFactory = viewModelFactory {
    initializer {
        SummaryViewModel(
            savedStateHandle = createSavedStateHandle(),
            repository = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container.tripRepository,
        )
    }
}

/** 정차 후 운행 결과. 운행이 끝난 뒤(P단·시동 off)에만 들어오는 화면 */
@Composable
fun SummaryScreen(
    onDone: () -> Unit,
    viewModel: SummaryViewModel = viewModel(factory = SummaryViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SummaryContent(uiState = uiState, onDone = onDone)
}

@Composable
fun SummaryContent(uiState: SummaryUiState, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 32.dp),
    ) {
        when (uiState) {
            SummaryUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            SummaryUiState.NotFound -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(stringResource(R.string.summary_not_found), style = MaterialTheme.typography.titleLarge)
                DoneButton(onDone)
            }
            is SummaryUiState.Loaded -> Loaded(uiState, onDone)
        }
    }
}

@Composable
private fun Loaded(state: SummaryUiState.Loaded, onDone: () -> Unit) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        // 왼쪽: 점수
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.summary_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScoreHeadline(state)
            if (state.totalTurns > 0) {
                Text(
                    stringResource(R.string.summary_turn_ratio, state.successTurns, state.totalTurns),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                stringResource(R.string.summary_distance, state.distanceKm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.weight(1f))
            DoneButton(onDone)
        }

        // 오른쪽: 회전 기록
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.summary_turns_title), style = MaterialTheme.typography.titleLarge)
            if (state.turns.isEmpty()) {
                Text(
                    stringResource(R.string.summary_turns_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.turns) { TurnRowItem(it) }
                }
            }
        }
    }
}

@Composable
private fun ScoreHeadline(state: SummaryUiState.Loaded) {
    val text = when (state.scoreState) {
        ScoreState.SCORED -> state.score?.let { stringResource(R.string.summary_score, it) }
            ?: stringResource(R.string.summary_pending)
        ScoreState.PENDING -> stringResource(R.string.summary_pending)
        ScoreState.NO_TURN -> stringResource(R.string.summary_no_turn)
        ScoreState.EXCLUDED_SHORT -> stringResource(R.string.summary_excluded_short)
        ScoreState.REJECTED -> stringResource(R.string.summary_rejected)
    }
    val isScore = state.scoreState == ScoreState.SCORED && state.score != null
    Text(
        text = text,
        style = if (isScore) {
            MaterialTheme.typography.displayMedium.copy(fontSize = 96.sp, fontWeight = FontWeight.Bold)
        } else {
            MaterialTheme.typography.headlineMedium
        },
        color = if (isScore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TurnRowItem(row: TurnRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(row.time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(if (row.direction == TurnDirection.LEFT) R.string.summary_turn_left else R.string.summary_turn_right),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        val (container, content) = if (row.signalOn) SuccessContainer to OnSuccessContainer else WarningContainer to OnWarningContainer
        Text(
            text = stringResource(if (row.signalOn) R.string.summary_signal_on else R.string.summary_signal_off),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(container)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DoneButton(onDone: () -> Unit) {
    Button(onClick = onDone, modifier = Modifier.heightIn(min = 64.dp).fillMaxWidth()) {
        Text(stringResource(R.string.summary_done), style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(widthDp = 960, heightDp = 720)
@Composable
private fun SummaryPreview() {
    BlinkerLeagueTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SummaryContent(
                uiState = SummaryUiState.Loaded(
                    scoreState = ScoreState.SCORED,
                    score = 50,
                    distanceKm = 1.8,
                    successTurns = 1,
                    totalTurns = 2,
                    turns = listOf(
                        TurnRow("08:02", TurnDirection.LEFT, signalOn = true),
                        TurnRow("08:04", TurnDirection.RIGHT, signalOn = false),
                    ),
                ),
                onDone = {},
            )
        }
    }
}

@Preview(widthDp = 960, heightDp = 720)
@Composable
private fun SummaryNoTurnPreview() {
    BlinkerLeagueTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SummaryContent(
                uiState = SummaryUiState.Loaded(ScoreState.NO_TURN, null, 1.2, 0, 0, emptyList()),
                onDone = {},
            )
        }
    }
}
