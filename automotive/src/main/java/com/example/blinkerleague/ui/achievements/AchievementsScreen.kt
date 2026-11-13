package com.example.blinkerleague.ui.achievements

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import java.time.ZoneId

private val AchievementsViewModelFactory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container
        val rewards = container.rewardRepository
        AchievementsViewModel(rewards::streak, rewards::badges, ZoneId.systemDefault(), { container.config.point })
    }
}

@Composable
fun AchievementsScreen(onOpenReport: () -> Unit, viewModel: AchievementsViewModel = viewModel(factory = AchievementsViewModelFactory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    AchievementsContent(state, onOpenReport)
}

@Composable
fun AchievementsContent(state: AchievementsUiState, onOpenReport: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        when (state) {
            AchievementsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            AchievementsUiState.Unavailable -> Text(
                stringResource(R.string.achievements_unavailable),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center),
            )
            is AchievementsUiState.Loaded -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    state.streak?.let { StreakPanel(it, Modifier.weight(1f).fillMaxWidth()) }
                    OutlinedButton(onClick = onOpenReport, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Text(stringResource(R.string.report_open), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Panel(Modifier.weight(1.2f).fillMaxHeight()) {
                    Text(stringResource(R.string.achievements_badges), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 220.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.badges) { BadgeItem(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakPanel(streak: StreakCard, modifier: Modifier) {
    Panel(modifier) {
        Text(stringResource(R.string.achievements_streak), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.achievements_streak_days, streak.days),
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 72.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(if (streak.todayAchieved) R.string.achievements_today_done else R.string.achievements_today_todo),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(R.string.achievements_reward_in, streak.daysToReward, streak.rewardPoint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BadgeItem(badge: BadgeCard) {
    val outline = if (badge.earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier.heightIn(min = 140.dp),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, outline),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                badge.name,
                style = MaterialTheme.typography.titleLarge,
                color = if (badge.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(badge.condition, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    !badge.earned -> stringResource(R.string.achievements_locked)
                    badge.awardedOn != null -> stringResource(R.string.achievements_earned_on, badge.awardedOn)
                    else -> stringResource(R.string.achievements_earned)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (badge.earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun AchievementsPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        AchievementsContent(
            AchievementsUiState.Loaded(
                StreakCard(5, false, 2),
                listOf(
                    BadgeCard("첫 경기", "점수가 난 첫 운행", true, "11.09"),
                    BadgeCard("7일 연속", "오늘의 퀘스트 7일 연속 달성", false, null),
                    BadgeCard("깜빡이 100회", "깜빡이를 켠 회전 누적 100회", false, null),
                ),
            ),
            onOpenReport = {},
        )
    }
}
