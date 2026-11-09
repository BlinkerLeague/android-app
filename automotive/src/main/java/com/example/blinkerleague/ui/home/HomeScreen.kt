package com.example.blinkerleague.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.BuildConfig
import com.example.blinkerleague.R
import com.example.blinkerleague.data.permission.RuntimePermission
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.service.RecordingStatus
import com.example.blinkerleague.ui.quest.MissionCard
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import java.time.ZoneId

private const val RECENT_LIMIT = 2

private val HomeViewModelFactory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container
        HomeViewModel(
            recentTrips = container.tripRepository.observeRecentResults(RECENT_LIMIT),
            recordingStatus = container.recordingStatus,
            speedPermission = container.permissions.status(RuntimePermission.SPEED),
            loadQuest = container.missionRepository::todayMission,
            loadStreak = container.rewardRepository::streak,
            refreshRecent = { container.tripRepository.refreshRecentScores(RECENT_LIMIT) },
            grades = container.config.grades,
            showDemoEntry = BuildConfig.DEBUG,
            clock = System::currentTimeMillis,
            zone = ZoneId.systemDefault(),
        )
    }
}

@Composable
fun HomeScreen(
    onOpenQuest: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    onOpenDemo: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 다른 화면이나 앱 밖에서 돌아오면 퀘스트·점수를 다시 받음
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    HomeContent(state, onOpenQuest, onOpenTrip, onOpenDemo)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenQuest: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    onOpenDemo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        state.notices.forEach { NoticeRow(it) }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            QuestPanel(state, onOpenQuest, Modifier.weight(1f).fillMaxHeight())
            state.streakDays?.let { StreakPanel(it, state.streakTodayAchieved, Modifier.weight(0.6f).fillMaxHeight()) }
            RecentTripsPanel(state.recentTrips, onOpenTrip, Modifier.weight(1f).fillMaxHeight())
        }
        StatusBar(state.recordingStatus, state.showDemoEntry, onOpenDemo)
    }
}

@Composable
private fun QuestPanel(state: HomeUiState, onOpenQuest: () -> Unit, modifier: Modifier) {
    val quest = state.quest
    if (quest != null) {
        Box(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenQuest)) {
            MissionCard(quest, Modifier.fillMaxSize())
        }
    } else {
        Panel(modifier) {
            Text(stringResource(R.string.home_quest_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(if (state.questLoading) R.string.home_quest_loading else R.string.mission_unavailable),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** 미션 연속 달성 (목업 홈 가운데 칸, 이모지 대신 글자) */
@Composable
private fun StreakPanel(days: Int, todayAchieved: Boolean, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.home_streak_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.achievements_streak_days, days), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(if (todayAchieved) R.string.home_streak_today_done else R.string.home_streak_today_todo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecentTripsPanel(rows: List<RecentTripRow>, onOpenTrip: (String) -> Unit, modifier: Modifier) {
    Panel(modifier) {
        Text(stringResource(R.string.home_recent_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (rows.isEmpty()) {
            Text(stringResource(R.string.home_recent_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RecentTripItem(row, onClick = { onOpenTrip(row.tripId) })
            }
        }
    }
}

@Composable
private fun RecentTripItem(row: RecentTripRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = when (row.day) {
                TripDay.TODAY -> stringResource(R.string.home_day_today, row.time)
                TripDay.YESTERDAY -> stringResource(R.string.home_day_yesterday, row.time)
                TripDay.OTHER -> stringResource(R.string.home_day_other, row.date, row.time)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        val (text, emphasized) = rowResult(row)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun rowResult(row: RecentTripRow): Pair<String, Boolean> = when (row.scoreState) {
    ScoreState.SCORED -> {
        val score = row.score ?: 0
        (row.grade?.let { stringResource(R.string.home_score_grade, score, it) } ?: stringResource(R.string.home_score, score)) to true
    }
    ScoreState.PENDING -> stringResource(R.string.home_state_pending) to false
    ScoreState.NO_TURN -> stringResource(R.string.home_state_no_turn) to false
    ScoreState.EXCLUDED_SHORT -> stringResource(R.string.home_state_excluded_short) to false
    ScoreState.REJECTED -> stringResource(R.string.home_state_rejected) to false
}

@Composable
private fun NoticeRow(notice: HomeNotice) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = stringResource(
                when (notice) {
                    HomeNotice.NO_SPEED_PERMISSION -> R.string.home_notice_no_speed
                    HomeNotice.RECORDING_OFF -> R.string.home_notice_recording_off
                },
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StatusBar(status: RecordingStatus, showDemoEntry: Boolean, onOpenDemo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val color = if (status == RecordingStatus.RECORDING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        Canvas(Modifier.size(14.dp)) { drawCircle(color) }
        Text(
            text = stringResource(
                when (status) {
                    RecordingStatus.OFF -> R.string.home_status_off
                    RecordingStatus.WAITING -> R.string.home_status_waiting
                    RecordingStatus.RECORDING -> R.string.home_status_recording
                },
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showDemoEntry) {
            OutlinedButton(onClick = onOpenDemo, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(R.string.home_demo), style = MaterialTheme.typography.labelLarge)
            }
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
private fun HomePreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        HomeContent(
            HomeUiState(
                quest = TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 2, 2, 3),
                questLoading = false,
                recentTrips = listOf(
                    RecentTripRow("a", TripDay.TODAY, "11.08", "08:42", ScoreState.SCORED, 100, "S"),
                    RecentTripRow("b", TripDay.YESTERDAY, "11.07", "18:10", ScoreState.EXCLUDED_SHORT, null, null),
                ),
                recordingStatus = RecordingStatus.WAITING,
                notices = listOf(HomeNotice.NO_SPEED_PERMISSION),
                showDemoEntry = true,
            ),
            onOpenQuest = {}, onOpenTrip = {}, onOpenDemo = {},
        )
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun HomeNarrowEmptyPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        HomeContent(HomeUiState(questLoading = false), onOpenQuest = {}, onOpenTrip = {}, onOpenDemo = {})
    }
}
