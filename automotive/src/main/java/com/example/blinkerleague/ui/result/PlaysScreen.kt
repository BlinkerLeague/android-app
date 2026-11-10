package com.example.blinkerleague.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.blinkerleague.ui.theme.SuccessContainer
import com.example.blinkerleague.ui.theme.WarningContainer
import java.time.ZoneId

private val PlaysViewModelFactory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container
        PlaysViewModel(container.tripRepository.observeResult(ResultViewModel.tripId(createSavedStateHandle())), ZoneId.systemDefault())
    }
}

/** 플레이 타임라인 (SCR-02a). 카드 탭 상세(SCR-02b)는 P1이라 탭 동작 없음 */
@Composable
fun PlaysScreen(onBack: () -> Unit, onOpenCard: () -> Unit, viewModel: PlaysViewModel = viewModel(factory = PlaysViewModelFactory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlaysContent(state, onBack, onOpenCard)
}

@Composable
fun PlaysContent(state: PlaysUiState, onBack: () -> Unit, onOpenCard: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (!state.loading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.plays_header, state.startedAt, state.endedAt, state.distanceKm, state.plays.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.result_goals, state.goals, state.conceded),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!state.loading && state.plays.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.plays_empty), style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    LazyRow(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(state.plays) { PlayCardItem(it, onOpenCard) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.plays_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(R.string.plays_back), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PlayCardItem(play: PlayCard, onClick: () -> Unit) {
    // 득점 파랑·실점 주황 (색만으로 구분하지 않도록 글자도 함께)
    val (container, content) = if (play.goal) SuccessContainer to OnSuccessContainer else WarningContainer to OnWarningContainer
    val label = when (play.direction) {
        TurnDirection.LEFT -> if (play.goal) R.string.plays_left_goal else R.string.plays_left_conceded
        TurnDirection.RIGHT -> if (play.goal) R.string.plays_right_goal else R.string.plays_right_conceded
    }
    // 카드를 누르면 깜빡이 항목 상세 (SCR-02b)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(240.dp).heightIn(min = 200.dp, max = 360.dp).fillMaxHeight(),
        color = container,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, content),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Text(play.time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (play.direction == TurnDirection.LEFT) "↰" else "↱", fontSize = 72.sp, color = content)
            Text(stringResource(label), style = MaterialTheme.typography.titleLarge, color = content)
            Text(
                stringResource(if (play.goal) R.string.plays_signal_on else R.string.plays_signal_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun PlaysPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        PlaysContent(
            PlaysUiState(
                loading = false, startedAt = "08:10", endedAt = "08:42", distanceKm = 14.2, goals = 2, conceded = 1,
                plays = listOf(
                    PlayCard("08:15", TurnDirection.LEFT, true),
                    PlayCard("08:27", TurnDirection.RIGHT, false),
                    PlayCard("08:33", TurnDirection.LEFT, true),
                ),
            ),
            onBack = {},
        )
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun PlaysNarrowEmptyPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        PlaysContent(
            PlaysUiState(loading = false, startedAt = "08:10", endedAt = "08:42", distanceKm = 14.2, goals = 0, conceded = 0, plays = emptyList()),
            onBack = {},
        )
    }
}
