package com.example.blinkerleague.ui.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.domain.model.ConditionType
import com.example.blinkerleague.domain.model.MissionCondition
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import com.example.blinkerleague.ui.theme.OnWarningContainer

private val QuestViewModelFactory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container
        QuestViewModel(container.missionRepository::todayMission, container.rewardRepository::streak) { container.config.point }
    }
}

@Composable
fun QuestScreen(onBack: () -> Unit, viewModel: QuestViewModel = viewModel(factory = QuestViewModelFactory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QuestContent(state, onBack)
}

@Composable
fun QuestContent(state: QuestUiState, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        when (state) {
            QuestUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            QuestUiState.Unavailable -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(stringResource(R.string.mission_unavailable), style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.mission_back), style = MaterialTheme.typography.labelLarge)
                }
            }
            is QuestUiState.Loaded -> Loaded(state.mission, onBack)
        }
    }
}

@Composable
private fun Loaded(mission: TodayMission, onBack: () -> Unit) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1.3f).fillMaxHeight()) {
            Text(stringResource(R.string.quest_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(mission.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                StatusText(mission.status)
            }
            Spacer(Modifier.heightIn(min = 8.dp))
            mission.conditions.forEachIndexed { i, condition ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ConditionLine(condition)
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            mission.rewardPoint?.let { point ->
                Text(stringResource(R.string.quest_reward_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 달성하면 포인트 원장에 적립됨 (지갑 화면)
                    Text(stringResource(R.string.quest_reward, point), style = MaterialTheme.typography.headlineMedium)
                }
            }
            Text(
                stringResource(
                    when (mission.status) {
                        MissionStatus.IN_PROGRESS -> R.string.mission_hint_in_progress
                        MissionStatus.ACHIEVED -> R.string.mission_hint_achieved
                        MissionStatus.FAILED -> R.string.mission_hint_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(
                    stringResource(if (mission.status == MissionStatus.ACHIEVED) R.string.quest_done else R.string.quest_retry),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun StatusText(status: MissionStatus) {
    val (text, color) = when (status) {
        MissionStatus.IN_PROGRESS -> R.string.mission_status_in_progress to MaterialTheme.colorScheme.primary
        MissionStatus.ACHIEVED -> R.string.mission_status_achieved to OnSuccessContainer
        MissionStatus.FAILED -> R.string.quest_status_failed to OnWarningContainer
    }
    Text(stringResource(text), style = MaterialTheme.typography.titleLarge, color = color)
}

@Composable
private fun ConditionLine(condition: MissionCondition) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (condition.type) {
                ConditionType.TURN_COUNT -> stringResource(R.string.quest_condition_turns, condition.target)
                ConditionType.SIGNAL_RATE -> stringResource(R.string.quest_condition_rate, condition.target)
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            when (condition.type) {
                ConditionType.TURN_COUNT -> stringResource(R.string.quest_progress_count, condition.current, condition.target)
                ConditionType.SIGNAL_RATE -> stringResource(R.string.quest_progress_rate, condition.current)
            },
            style = MaterialTheme.typography.titleLarge,
            color = if (condition.met) OnSuccessContainer else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun QuestPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        QuestContent(QuestUiState.Loaded(TodayMission("회전 깜빡이 완벽", MissionStatus.FAILED, 2, 1, 3, rewardPoint = 20)), onBack = {})
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun QuestNarrowPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        QuestContent(QuestUiState.Loaded(TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 1, 1, 3, rewardPoint = 20)), onBack = {})
    }
}
