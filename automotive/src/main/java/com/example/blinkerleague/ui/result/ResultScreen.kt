package com.example.blinkerleague.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
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
import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.MatchOutcome
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.ui.common.game.Confetti
import com.example.blinkerleague.ui.common.game.CountUpText
import com.example.blinkerleague.ui.common.game.GradeStamp
import com.example.blinkerleague.ui.common.game.HatchedBox
import com.example.blinkerleague.ui.common.game.RevealClock
import com.example.blinkerleague.ui.common.game.reveal
import com.example.blinkerleague.ui.common.game.rememberRevealClock
import com.example.blinkerleague.ui.common.game.skipRevealOnTap
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.Gold
import com.example.blinkerleague.ui.theme.GoldContainer
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import com.example.blinkerleague.ui.theme.OnWarningContainer
import com.example.blinkerleague.ui.theme.SuccessContainer
import com.example.blinkerleague.ui.theme.gradeColor
import java.time.ZoneId

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
            loadDayOrdinal = { container.tripRepository.dayOrdinal(tripId, ZoneId.systemDefault()) },
            tips = container.config.tips,
            loadMetrics = { container.tripRepository.metricDetails(tripId) },
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
    ResultContent(state, onOpenPlays, onOpenQuest, onDone, onOpenMetric, grades = viewModel.grades)
}

@Composable
fun ResultContent(
    state: ResultUiState,
    onOpenPlays: () -> Unit,
    onOpenQuest: () -> Unit,
    onDone: () -> Unit,
    onOpenMetric: (MetricItem) -> Unit = {},
    /** 등급색을 고를 기준값 (등급 이름이 아니라 순서로 고름) */
    grades: List<GradeThreshold> = GradeThreshold.DEFAULT,
) {
    Box(Modifier.fillMaxSize()) {
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
            is ResultUiState.Loaded -> Loaded(state, grades, onOpenPlays, onOpenQuest, onDone, onOpenMetric)
        }
    }
}

/**
 * 진입 연출 순서 (ms, 목업 7장): 왼쪽 판 0 → 스코어 카운트업 200 → 오른쪽 판 300 → 스탯 막대 500~ → 승패 문구 800
 * → 등급 도장 900 → 평점 카운트업 1000 → 팁 1100 → 꽃가루 1200(2초) → 포인트 1500 → 퀘스트 배너 1800. 탭하면 끝으로 건너뜀
 */
private object ResultMotion {
    const val SCORE = 200
    const val RIGHT_PANEL = 300
    const val STATS = 500
    const val OUTCOME = 800
    const val STAMP = 900
    const val RATING = 1_000
    const val TIP = 1_100
    const val CONFETTI = 1_200
    const val POINTS = 1_500
    const val QUEST = 1_800
    const val TOTAL = 3_200
}

@Composable
private fun Loaded(
    state: ResultUiState.Loaded,
    grades: List<GradeThreshold>,
    onOpenPlays: () -> Unit,
    onOpenQuest: () -> Unit,
    onDone: () -> Unit,
    onOpenMetric: (MetricItem) -> Unit,
) {
    val clock = rememberRevealClock(ResultMotion.TOTAL)
    Box(Modifier.fillMaxSize().skipRevealOnTap(clock)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Panel(Modifier.weight(0.8f).fillMaxHeight().reveal(clock, 0)) {
                Scoreboard(state, grades, clock)
            }
            Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Panel(Modifier.weight(1f).fillMaxWidth().reveal(clock, ResultMotion.RIGHT_PANEL)) {
                    Stats(state, clock, onOpenMetric)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    state.mission?.let {
                        QuestBanner(it, onOpenQuest, Modifier.weight(1f).reveal(clock, ResultMotion.QUEST, fromY = 0.dp, fromX = 24.dp))
                    } ?: Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onOpenPlays, modifier = Modifier.heightIn(min = 64.dp)) {
                        Text(stringResource(R.string.result_open_plays), style = MaterialTheme.typography.labelLarge)
                    }
                    DoneButton(onDone)
                }
            }
        }
        // 맨 위 등급일 때만 한 번 (터치를 받지 않음)
        if (state.celebrate) Confetti(clock, ResultMotion.CONFETTI)
    }
}

/** 왼쪽: 머리말 · 스코어보드 · 승패 · 등급 도장 · 평점 · 다음 등급까지 · 적립 포인트 */
@Composable
private fun Scoreboard(state: ResultUiState.Loaded, grades: List<GradeThreshold>, clock: RevealClock) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            state.dayOrdinal?.let { stringResource(R.string.result_header_today, it, state.distanceKm) }
                ?: stringResource(R.string.result_header, state.distanceKm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (state.scoreState == ScoreState.EXCLUDED_SHORT) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Message(R.string.result_excluded_short, null) }
        } else {
            GoalsBoard(state, clock)
            state.outcome?.let { OutcomeLabel(it, Modifier.reveal(clock, ResultMotion.OUTCOME)) }
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                // 도장 · 평점 · 다음 등급을 남은 높이에 고르게 (도장은 남은 높이만큼 커짐, 최대 240dp)
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Rating(state, grades, clock)
            }
            state.earnedPoints?.let { PointsCard(it, clock, Modifier.fillMaxWidth().reveal(clock, ResultMotion.POINTS)) }
        }
    }
}

/** 득점 : 실점 큰 숫자. 회전이 없으면 "오늘은 회전 없는 경기" */
@Composable
private fun GoalsBoard(state: ResultUiState.Loaded, clock: RevealClock) {
    val shape = RoundedCornerShape(16.dp)
    val description = stringResource(R.string.result_goals, state.goals, state.conceded)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.goals + state.conceded == 0) {
            Text(stringResource(R.string.result_no_turn_match), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        } else {
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoalCount(R.string.result_goals_label, state.goals, OnSuccessContainer, clock, Modifier.weight(1f))
                Text(":", fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GoalCount(R.string.result_conceded_label, state.conceded, OnWarningContainer, clock, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GoalCount(label: Int, count: Int, color: Color, clock: RevealClock, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CountUpText(count, clock, ResultMotion.SCORE, style = fontStyle(64), color = color)
    }
}

@Composable
private fun fontStyle(size: Int) = MaterialTheme.typography.displayMedium.copy(fontSize = size.sp, fontWeight = FontWeight.Black)

@Composable
private fun OutcomeLabel(outcome: MatchOutcome, modifier: Modifier) {
    val (text, color) = when (outcome) {
        MatchOutcome.PERFECT -> R.string.result_outcome_perfect to OnSuccessContainer
        MatchOutcome.WIN -> R.string.result_outcome_win to OnSuccessContainer
        MatchOutcome.DRAW -> R.string.result_outcome_draw to MaterialTheme.colorScheme.onSurface
        MatchOutcome.TOUGH -> R.string.result_outcome_tough to OnWarningContainer
    }
    Text(stringResource(text), modifier = modifier, style = MaterialTheme.typography.titleLarge, color = color)
}

/** 가운데: 점수가 나왔으면 등급 도장 + 평점, 아니면 상태 문구 */
@Composable
private fun ColumnScope.Rating(state: ResultUiState.Loaded, grades: List<GradeThreshold>, clock: RevealClock) {
    when (state.scoreState) {
        ScoreState.SCORED -> {
            state.grade?.let {
                GradeStamp(
                    grade = it,
                    color = gradeColor(it, grades),
                    clock = clock,
                    startMs = ResultMotion.STAMP,
                    modifier = Modifier.weight(1f, fill = false).heightIn(min = 96.dp, max = 240.dp),
                    glow = state.celebrate,
                )
            }
            state.score?.let { score ->
                CountUpText(
                    score,
                    clock,
                    ResultMotion.RATING,
                    style = MaterialTheme.typography.headlineMedium,
                    format = { stringResource(R.string.result_score, it) },
                )
            }
            state.nextGrade?.let {
                Text(
                    stringResource(R.string.result_next_grade, it.grade, it.points),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.reveal(clock, ResultMotion.RATING),
                )
            }
        }
        ScoreState.PENDING -> Message(R.string.result_pending, R.string.result_pending_body)
        ScoreState.NO_TURN -> Message(null, R.string.result_no_turn_body)
        ScoreState.REJECTED -> Message(R.string.result_rejected, null)
        ScoreState.EXCLUDED_SHORT -> Unit
    }
}

@Composable
private fun Message(title: Int?, body: Int?) {
    title?.let { Text(stringResource(it), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) }
    body?.let {
        Text(
            stringResource(it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "+NP 적립" 카운트업. 0P · 모름이면 상태에서 null이라 그리지 않음 */
@Composable
private fun PointsCard(points: Int, clock: RevealClock, modifier: Modifier) {
    Surface(modifier, color = GoldContainer, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f))) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            CountUpText(
                points,
                clock,
                ResultMotion.POINTS,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = Gold,
                format = { stringResource(R.string.result_points, it) },
            )
        }
    }
}

/** 오른쪽: 항목별 스탯 막대 + 다음 경기 팁 */
@Composable
private fun Stats(state: ResultUiState.Loaded, clock: RevealClock, onOpenMetric: (MetricItem) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.result_metrics_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.result_metrics_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.metrics.forEachIndexed { index, row ->
            MetricLine(row, clock, ResultMotion.STATS + index * 100, onClick = { onOpenMetric(row.item) })
        }
        state.tip?.let { TipCard(it, Modifier.fillMaxWidth().reveal(clock, ResultMotion.TIP, fromX = 40.dp, fromY = 0.dp)) }
    }
}

@Composable
private fun MetricLine(row: MetricRow, clock: RevealClock, startMs: Int, onClick: () -> Unit) {
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
    val status = when (value) {
        is MetricValue.Score -> null
        MetricValue.Pending -> R.string.result_metric_pending
        MetricValue.NoTurn -> R.string.result_metric_no_turn
        MetricValue.Unsupported -> R.string.result_metric_unsupported
        MetricValue.Excluded -> R.string.result_metric_excluded
        MetricValue.NotScored -> R.string.result_metric_not_scored
        MetricValue.NotApplicable -> R.string.result_metric_not_applicable
        MetricValue.ComingSoon -> R.string.result_metric_coming_soon
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(0.4f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = textColor)
            status?.let { Text(stringResource(it), style = MaterialTheme.typography.labelMedium, color = textColor) }
        }
        val barModifier = Modifier.weight(0.45f).height(16.dp)
        when (value) {
            // 아직 측정하지 않는 항목은 사선 무늬 (0점처럼 보이지 않게)
            MetricValue.ComingSoon, MetricValue.Unsupported -> HatchedBox(barModifier)
            else -> {
                val fraction = if (value is MetricValue.Score) value.score.coerceIn(0, 100) / 100f else 0f
                val fill = if (value is MetricValue.Score && value.score >= 70) OnSuccessContainer else OnWarningContainer
                Box(barModifier.clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.outlineVariant)) {
                    val filled = fraction * clock.progress(startMs, 900)
                    if (filled > 0f) {
                        Box(Modifier.fillMaxWidth(filled).fillMaxHeight().clip(RoundedCornerShape(percent = 50)).background(fill))
                    }
                }
            }
        }
        Text(
            if (value is MetricValue.Score) value.score.toString() else "—",
            style = MaterialTheme.typography.titleLarge,
            color = textColor,
            modifier = Modifier.weight(0.15f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun TipCard(tip: String, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.result_tip_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tip, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 오늘 퀘스트 배너 (누르면 퀘스트 화면) */
@Composable
private fun QuestBanner(mission: TodayMission, onOpenQuest: () -> Unit, modifier: Modifier) {
    val (status, statusColor) = when (mission.status) {
        MissionStatus.IN_PROGRESS -> R.string.mission_status_in_progress to MaterialTheme.colorScheme.onSurfaceVariant
        MissionStatus.ACHIEVED -> R.string.mission_status_achieved to OnSuccessContainer
        MissionStatus.FAILED -> R.string.mission_status_failed to OnWarningContainer
    }
    val shape = RoundedCornerShape(percent = 50)
    Surface(
        modifier = modifier.heightIn(min = 64.dp).clip(shape).clickable(onClick = onOpenQuest),
        color = SuccessContainer,
        shape = shape,
        border = BorderStroke(1.dp, OnSuccessContainer.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.result_quest, mission.title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(stringResource(status), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = statusColor, maxLines = 1)
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
    goals: Int = 3,
    conceded: Int = 1,
    turnValue: MetricValue = if (score != null) MetricValue.Score(score) else MetricValue.Pending,
) = ResultUiState.Loaded(
    scoreState = state, score = score, grade = grade, distanceKm = 3.9, goals = goals, conceded = conceded,
    metrics = listOf(
        MetricRow(MetricItem.TURN_SIGNAL, turnValue),
        MetricRow(MetricItem.SMOOTH_DRIVING, MetricValue.ComingSoon),
        MetricRow(MetricItem.SAFETY_WARNING, MetricValue.Unsupported),
        MetricRow(MetricItem.NIGHT_HEADLIGHT, MetricValue.ComingSoon),
    ),
    mission = TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 3, 2, 3),
    outcome = MatchOutcome.of(goals, conceded).takeIf { state != ScoreState.EXCLUDED_SHORT },
)

private val TopGradeState = previewState(ScoreState.SCORED, 100, "S", goals = 4, conceded = 0)
    .copy(celebrate = true, earnedPoints = 3, dayOrdinal = 3)
private val SecondGradeState = previewState(ScoreState.SCORED, 86, "A")
    .copy(earnedPoints = 3, dayOrdinal = 3, nextGrade = NextGrade("S", 9), tip = "우회전도 회전 5초 전에 깜빡이를 켜 보세요")

@Composable
private fun ResultPreview(state: ResultUiState) = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) { ResultContent(state, {}, {}, {}) }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ResultTopGradePreview() = ResultPreview(TopGradeState)

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun ResultTopGradeNarrowPreview() = ResultPreview(TopGradeState)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ResultSecondGradePreview() = ResultPreview(SecondGradeState)

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun ResultSecondGradeNarrowPreview() = ResultPreview(SecondGradeState)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ResultPendingPreview() = ResultPreview(previewState(ScoreState.PENDING, null, null, goals = 1, conceded = 1))

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun ResultPendingNarrowPreview() = ResultPreview(previewState(ScoreState.PENDING, null, null, goals = 1, conceded = 3))

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ResultNoTurnPreview() = ResultPreview(previewState(ScoreState.NO_TURN, null, null, goals = 0, conceded = 0, turnValue = MetricValue.NoTurn))

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun ResultNoTurnNarrowPreview() = ResultPreview(previewState(ScoreState.NO_TURN, null, null, goals = 0, conceded = 0, turnValue = MetricValue.NoTurn))

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ResultExcludedShortPreview() = ResultPreview(
    previewState(ScoreState.EXCLUDED_SHORT, null, null, goals = 1, conceded = 0, turnValue = MetricValue.Excluded).copy(distanceKm = 0.6),
)

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun ResultExcludedShortNarrowPreview() = ResultPreview(
    previewState(ScoreState.EXCLUDED_SHORT, null, null, goals = 1, conceded = 0, turnValue = MetricValue.Excluded).copy(distanceKm = 0.6),
)
