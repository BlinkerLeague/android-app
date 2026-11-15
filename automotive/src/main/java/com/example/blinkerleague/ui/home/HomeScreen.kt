package com.example.blinkerleague.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import com.example.blinkerleague.BuildConfig
import com.example.blinkerleague.R
import com.example.blinkerleague.data.permission.RuntimePermission
import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.MatchOutcome
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.service.RecordingStatus
import com.example.blinkerleague.ui.achievements.StreakCard
import com.example.blinkerleague.ui.achievements.streakDotsFilled
import com.example.blinkerleague.ui.common.game.GradeStamp
import com.example.blinkerleague.ui.common.game.RevealClock
import com.example.blinkerleague.ui.common.game.StreakDots
import com.example.blinkerleague.ui.common.game.StreakFlame
import com.example.blinkerleague.ui.common.game.rememberRevealClock
import com.example.blinkerleague.ui.common.game.reveal
import com.example.blinkerleague.ui.common.game.skipRevealOnTap
import com.example.blinkerleague.ui.quest.MissionCard
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.Flame
import com.example.blinkerleague.ui.theme.Gold
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import com.example.blinkerleague.ui.theme.OnWarningContainer
import com.example.blinkerleague.ui.theme.SuccessContainer
import com.example.blinkerleague.ui.theme.gradeColor
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
            rule = { container.config.point },
            profile = container::profile,
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
    HomeContent(state, onOpenQuest, onOpenTrip, onOpenDemo, grades = viewModel.grades)
}

/**
 * 진입 연출 순서 (ms, 목업 2번): 선수 카드 0 → 퀘스트 150(링 채움 300~1200) → 연승 300 → 최근 경기 450 → 등급 도장 700~.
 * 정차 화면에서만 재생하고(LocalParked), 한 번 본 뒤에는 다시 재생하지 않음. 탭하면 끝으로 건너뜀
 */
private object HomeMotion {
    const val QUEST = 150
    const val RING = 300
    const val RING_DURATION = 900
    const val STREAK = 300
    const val RECENT = 450
    const val STAMP = 700
    const val STAMP_GAP = 150
    const val TOTAL = 1_600
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenQuest: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    onOpenDemo: () -> Unit,
    grades: List<GradeThreshold> = GradeThreshold.DEFAULT,
) {
    val clock = rememberRevealClock(HomeMotion.TOTAL)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .skipRevealOnTap(clock)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.notices.forEach { NoticeRow(it) }
        state.player?.let { PlayerCardRow(it, Modifier.fillMaxWidth().reveal(clock, 0)) }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            QuestPanel(state, onOpenQuest, clock, Modifier.weight(1.15f).fillMaxHeight().reveal(clock, HomeMotion.QUEST))
            state.streak?.let { StreakPanel(it, Modifier.weight(0.85f).fillMaxHeight().reveal(clock, HomeMotion.STREAK)) }
            RecentTripsPanel(state.recentTrips, grades, clock, onOpenTrip, Modifier.weight(1f).fillMaxHeight().reveal(clock, HomeMotion.RECENT))
        }
        StatusBar(state.recordingStatus, state.showDemoEntry, onOpenDemo)
    }
}

/** 선수 카드: 닉네임 · 지역만 (경험치 · 리그 단계 · 포인트는 데이터가 없어 넣지 않음, plan-ui 결정 2 · 6) */
@Composable
private fun PlayerCardRow(player: PlayerCard, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier
                .background(Brush.horizontalGradient(listOf(SuccessContainer, MaterialTheme.colorScheme.surface)))
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, OnSuccessContainer))),
                contentAlignment = Alignment.Center,
            ) {
                Text(player.nickname.take(1), fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                player.nickname,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            player.regionLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(SuccessContainer)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun QuestPanel(state: HomeUiState, onOpenQuest: () -> Unit, clock: RevealClock, modifier: Modifier) {
    val quest = state.quest
    if (quest != null) {
        Box(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenQuest)) {
            MissionCard(quest, Modifier.fillMaxSize(), fill = clock.progress(HomeMotion.RING, HomeMotion.RING_DURATION))
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

/** 퀘스트 연승: 불꽃(정차 중에만 아주 느리게) · N연승 · 주기 칸 점 · 다음 연속 보상까지 (기준값 point.streakRewardDays · streakRewardPoint) */
@Composable
private fun StreakPanel(streak: StreakCard, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StreakFlame(Modifier.weight(1f, fill = false).heightIn(max = 88.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 56.sp)) { append(streak.days.toString()) }
                    withStyle(SpanStyle(fontSize = 24.sp)) { append(stringResource(R.string.game_streak_unit)) }
                },
                fontWeight = FontWeight.Black,
                color = Flame,
                maxLines = 1,
            )
            Text(
                stringResource(if (streak.todayAchieved) R.string.home_streak_today_done else R.string.home_streak_today_todo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            StreakDots(streakDotsFilled(streak.days, streak.period), streak.period, Modifier.fillMaxWidth().height(18.dp))
            Text(
                stringResource(R.string.game_streak_next_reward, streak.daysToReward, streak.days + streak.daysToReward, streak.rewardPoint),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Gold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecentTripsPanel(
    rows: List<RecentTripRow>,
    grades: List<GradeThreshold>,
    clock: RevealClock,
    onOpenTrip: (String) -> Unit,
    modifier: Modifier,
) {
    Panel(modifier) {
        Text(stringResource(R.string.home_recent_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (rows.isEmpty()) {
            Text(stringResource(R.string.home_recent_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { i, row ->
                RecentTripItem(row, grades, clock, HomeMotion.STAMP + i * HomeMotion.STAMP_GAP, onClick = { onOpenTrip(row.tripId) })
            }
        }
    }
}

/** 최근 경기 한 줄: 등급 도장 · "3 : 1 승리" · "오늘 08:42 · 3.9km" · 평점 */
@Composable
private fun RecentTripItem(row: RecentTripRow, grades: List<GradeThreshold>, clock: RevealClock, stampMs: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val grade = row.grade
        if (grade != null && row.scoreState == ScoreState.SCORED) {
            GradeStamp(grade, gradeColor(grade, grades), clock, stampMs, Modifier.size(52.dp))
        } else {
            Spacer(Modifier.width(52.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(matchLine(row), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Text(
                stringResource(R.string.game_recent_meta, dayText(row), row.distanceKm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        sideText(row)?.let { (text, emphasized) ->
            Text(
                text,
                style = if (emphasized) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** 경기로 셀 수 있으면(점수 · 집계 중이고 회전이 있으면) "3 : 1 승리", 아니면 상태 문구 */
@Composable
private fun matchLine(row: RecentTripRow) = buildAnnotatedString {
    val outcome = row.outcome
    if (outcome != null && (row.scoreState == ScoreState.SCORED || row.scoreState == ScoreState.PENDING)) {
        withStyle(SpanStyle(color = OnSuccessContainer)) { append(row.goals.toString()) }
        append(" : ")
        withStyle(SpanStyle(color = OnWarningContainer)) { append(row.conceded.toString()) }
        append(" ")
        append(stringResource(outcomeText(outcome)))
    } else {
        append(
            stringResource(
                when (row.scoreState) {
                    ScoreState.EXCLUDED_SHORT -> R.string.home_state_excluded_short
                    ScoreState.REJECTED -> R.string.home_state_rejected
                    else -> R.string.home_state_no_turn
                },
            ),
        )
    }
}

private fun outcomeText(outcome: MatchOutcome) = when (outcome) {
    MatchOutcome.PERFECT -> R.string.game_outcome_perfect
    MatchOutcome.WIN -> R.string.game_outcome_win
    MatchOutcome.DRAW -> R.string.game_outcome_draw
    MatchOutcome.TOUGH -> R.string.game_outcome_tough
}

/** 오른쪽: 점수가 나면 "평점 N", 집계 중이면 그 문구 */
@Composable
private fun sideText(row: RecentTripRow): Pair<String, Boolean>? = when (row.scoreState) {
    ScoreState.SCORED -> row.score?.let { stringResource(R.string.home_score, it) to true }
    ScoreState.PENDING -> stringResource(R.string.home_state_pending) to false
    else -> null
}

@Composable
private fun dayText(row: RecentTripRow) = when (row.day) {
    TripDay.TODAY -> stringResource(R.string.home_day_today, row.time)
    TripDay.YESTERDAY -> stringResource(R.string.home_day_yesterday, row.time)
    TripDay.OTHER -> stringResource(R.string.home_day_other, row.date, row.time)
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

/** 상태줄. 점은 색만 바뀌고 깜빡이지 않음(점멸 없음) */
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

private val previewState = HomeUiState(
    quest = TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 2, 2, 3),
    questLoading = false,
    recentTrips = listOf(
        RecentTripRow("a", TripDay.TODAY, "11.08", "22:43", ScoreState.SCORED, 88, "A", goals = 3, conceded = 1, outcome = MatchOutcome.WIN, distanceKm = 3.9),
        RecentTripRow("b", TripDay.TODAY, "11.08", "08:12", ScoreState.SCORED, 100, "S", goals = 4, conceded = 0, outcome = MatchOutcome.PERFECT, distanceKm = 12.3),
    ),
    streak = StreakCard(days = 5, todayAchieved = true, daysToReward = 2),
    player = PlayerCard("깜빡왕", "서울 강남구"),
    recordingStatus = RecordingStatus.WAITING,
    showDemoEntry = true,
)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomePreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        HomeContent(previewState, onOpenQuest = {}, onOpenTrip = {}, onOpenDemo = {})
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun HomeNarrowPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        HomeContent(
            previewState.copy(
                quest = TodayMission("회전 깜빡이 완벽", MissionStatus.ACHIEVED, 3, 3, 3),
                recentTrips = listOf(
                    RecentTripRow("c", TripDay.YESTERDAY, "11.07", "18:10", ScoreState.PENDING, null, null, goals = 1, conceded = 1, outcome = MatchOutcome.DRAW, distanceKm = 6.2),
                    RecentTripRow("d", TripDay.OTHER, "11.06", "08:00", ScoreState.EXCLUDED_SHORT, null, null, distanceKm = 0.6),
                ),
                notices = listOf(HomeNotice.NO_SPEED_PERMISSION),
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
