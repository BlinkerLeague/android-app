package com.example.blinkerleague.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme

private val DemoViewModelFactory = viewModelFactory {
    initializer { DemoViewModel((this[APPLICATION_KEY] as BlinkerLeagueApplication).container) }
}

@Composable
fun DemoScreen(
    onTripFinished: (tripId: String) -> Unit,
    onOpenMissions: () -> Unit,
    viewModel: DemoViewModel = viewModel(factory = DemoViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val phase = uiState.phase
    LaunchedEffect(phase) {
        if (phase is DemoPhase.Finished) {
            onTripFinished(phase.tripId)
            viewModel.onNavigated()
        }
    }

    DemoContent(
        uiState = uiState,
        onSelectSpeed = viewModel::selectSpeed,
        onPlay = viewModel::play,
        onStop = viewModel::stop,
        onOpenMissions = onOpenMissions,
    )
}

@Composable
fun DemoContent(
    uiState: DemoUiState,
    onSelectSpeed: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    onStop: () -> Unit,
    onOpenMissions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        when (val phase = uiState.phase) {
            is DemoPhase.Running -> RunningPanel(phase, onStop)
            else -> ScenarioPicker(uiState, onSelectSpeed, onPlay, onOpenMissions)
        }
    }
}

@Composable
private fun ScenarioPicker(
    uiState: DemoUiState,
    onSelectSpeed: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    onOpenMissions: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.demo_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.demo_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onOpenMissions, modifier = Modifier.heightIn(min = 64.dp)) {
            Text(stringResource(R.string.mission_open), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.demo_speed), style = MaterialTheme.typography.labelMedium)
            SPEED_OPTIONS.forEach { speed ->
                FilterChip(
                    selected = uiState.speed == speed,
                    onClick = { onSelectSpeed(speed) },
                    label = {
                        Text(stringResource(R.string.demo_speed_option, speed), style = MaterialTheme.typography.labelLarge)
                    },
                    modifier = Modifier.heightIn(min = 64.dp),
                )
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(uiState.scenarios, key = { it.index }) { item ->
            Card(
                onClick = { onPlay(item.index) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.demo_duration, item.durationSec / uiState.speed.coerceAtLeast(1)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 주행 중 화면: 점수·판정은 보여주지 않고 진행 상황만 */
@Composable
private fun RunningPanel(phase: DemoPhase.Running, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(phase.scenarioName, style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(if (phase.recording) R.string.demo_running_recording else R.string.demo_running_waiting),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = phase.progress,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStop, modifier = Modifier.heightIn(min = 64.dp)) {
            Text(stringResource(R.string.demo_stop), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(widthDp = 960, heightDp = 720)
@Composable
private fun DemoPickerPreview() {
    BlinkerLeagueTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DemoContent(
                uiState = DemoUiState(
                    scenarios = listOf(ScenarioItem(0, "좌회전 + 깜빡이", 52), ScenarioItem(1, "회전교차로", 62)),
                ),
                onSelectSpeed = {}, onPlay = {}, onStop = {}, onOpenMissions = {},
            )
        }
    }
}

@Preview(widthDp = 960, heightDp = 720)
@Composable
private fun DemoRunningPreview() {
    BlinkerLeagueTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DemoContent(
                uiState = DemoUiState(emptyList(), phase = DemoPhase.Running("시연", 0.4f, recording = true)),
                onSelectSpeed = {}, onPlay = {}, onStop = {}, onOpenMissions = {},
            )
        }
    }
}
