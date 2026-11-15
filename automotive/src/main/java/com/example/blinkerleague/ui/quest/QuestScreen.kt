package com.example.blinkerleague.ui.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.blinkerleague.ui.common.game.Confetti
import com.example.blinkerleague.ui.common.game.RevealClock
import com.example.blinkerleague.ui.common.game.StreakFlame
import com.example.blinkerleague.ui.common.game.rememberRevealClock
import com.example.blinkerleague.ui.common.game.reveal
import com.example.blinkerleague.ui.common.game.skipRevealOnTap
import com.example.blinkerleague.ui.theme.Flame
import com.example.blinkerleague.ui.theme.Gold
import com.example.blinkerleague.ui.theme.SuccessContainer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
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
        QuestViewModel(
            container.missionRepository::todayMission,
            container.rewardRepository::streak,
            celebrations = container.questCelebrations,
        ) { container.config.point }
    }
}

@Composable
fun QuestScreen(onBack: () -> Unit, viewModel: QuestViewModel = viewModel(factory = QuestViewModelFactory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QuestContent(state, onBack, viewModel::onCelebrated)
}

@Composable
fun QuestContent(state: QuestUiState, onBack: () -> Unit, onCelebrated: () -> Unit = {}) {
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
            is QuestUiState.Loaded -> Loaded(state, onBack, onCelebrated)
        }
    }
}

/**
 * 진입 연출 순서 (ms): 왼쪽 판 0 → 오른쪽 판 200 → 조건 막대 채움 300~1100 → CLEAR 도장 900 → 꽃가루 1000(클리어 회차당 1회, plan-ui 결정 11).
 * 정차 화면에서만 재생하고 한 번 본 뒤에는 다시 재생하지 않음. 탭하면 끝으로 건너뜀
 */
private object QuestMotion {
    const val RIGHT_PANEL = 200
    const val FILL = 300
    const val FILL_DURATION = 800
    const val STAMP = 900
    const val CONFETTI = 1_000
    const val TOTAL = 3_000
}

@Composable
private fun Loaded(state: QuestUiState.Loaded, onBack: () -> Unit, onCelebrated: () -> Unit) {
    val mission = state.mission
    val clock = rememberRevealClock(QuestMotion.TOTAL)
    val cleared = mission.status == MissionStatus.ACHIEVED
    Box(Modifier.fillMaxSize().skipRevealOnTap(clock)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Panel(Modifier.weight(1.2f).fillMaxHeight().reveal(clock, 0)) {
                Text(stringResource(R.string.quest_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(mission.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f, fill = false))
                    if (cleared) ClearStamp(clock) else StatusText(mission.status)
                }
                Spacer(Modifier.heightIn(min = 8.dp))
                val fill = clock.progress(QuestMotion.FILL, QuestMotion.FILL_DURATION)
                mission.conditions.forEach { Checkpoint(it, fill) }
                Spacer(Modifier.weight(1f))
                state.streakImpact?.let { StreakImpactLine(it) }
            }
            Panel(Modifier.weight(1f).fillMaxHeight().reveal(clock, QuestMotion.RIGHT_PANEL)) {
                mission.rewardPoint?.let { point ->
                    Text(stringResource(R.string.quest_reward_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 달성하면 포인트 원장에 적립됨 (지갑 화면). 보물상자 · 보상 받기 · 일일 상한 줄은 넣지 않음 (plan-ui 결정 2)
                    Text(stringResource(R.string.quest_reward, point), style = MaterialTheme.typography.headlineMedium, color = Gold)
                }
                Text(
                    questHintText(QuestHint.of(mission)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Text(
                        stringResource(if (cleared) R.string.quest_done else R.string.quest_retry),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        // 퀘스트 클리어 회차당 한 번 (터치를 받지 않음). 꽃가루가 시작되면(또는 건너뛰면) 본 것으로 기억
        if (state.celebrate) {
            Confetti(clock, QuestMotion.CONFETTI)
            LaunchedEffect(clock) {
                snapshotFlow { clock.elapsedMs >= QuestMotion.CONFETTI }.first { it }
                onCelebrated()
            }
        }
    }
}

/** "CLEAR" 도장: 작게 기울어진 상태에서 커지며 찍힘 (반복 · 점멸 없음) */
@Composable
private fun ClearStamp(clock: RevealClock) {
    Text(
        stringResource(R.string.game_quest_clear),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
        color = SuccessContainer,
        maxLines = 1,
        modifier = Modifier
            .graphicsLayer {
                val p = clock.progress(QuestMotion.STAMP, 500)
                alpha = p
                scaleX = 1.4f - 0.4f * p
                scaleY = scaleX
                rotationZ = -6f * p
            }
            .clip(RoundedCornerShape(percent = 50))
            .background(OnSuccessContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
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

/** 조건 체크포인트: 체크 원 · 조건 · 진행(회전 수는 칸, 사용률은 막대) · 숫자 */
@Composable
private fun Checkpoint(condition: MissionCondition, fill: Float) {
    val color = if (condition.met) OnSuccessContainer else MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CheckMark(condition.met, Modifier.size(48.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(conditionLabel(condition), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            when (condition.type) {
                ConditionType.TURN_COUNT -> TurnCells(condition.current, condition.target, fill, color)
                ConditionType.SIGNAL_RATE -> FillBar(condition.current / 100f * fill, color)
            }
        }
        Text(conditionProgress(condition), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = color)
    }
}

/** 회전 수 칸: 필요한 회전 수만큼 칸, 한 회전이 한 칸. 칸이 너무 많으면(10 초과) 막대로 */
@Composable
private fun TurnCells(current: Int, target: Int, fill: Float, color: Color) {
    if (target !in 1..MAX_CELLS) {
        FillBar(if (target > 0) current.toFloat() / target * fill else 0f, color)
        return
    }
    val shown = (current.coerceAtMost(target) * fill).toInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(target) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (i < shown) color else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

private const val MAX_CELLS = 10

@Composable
private fun FillBar(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(12.dp).clip(RoundedCornerShape(percent = 50)).background(color))
    }
}

/** 연승 영향: "클리어하면 6연승" / "오늘 클리어로 6연승", 연속 보상 주기면 "· 연속 보상 +50P"(오늘 클리어면 "… 받았어요") */
@Composable
private fun StreakImpactLine(impact: StreakImpact) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StreakFlame(Modifier.height(28.dp))
        val days = stringResource(R.string.game_streak_days, impact.days)
        Text(
            buildAnnotatedString {
                // 보상 줄은 한 문자열로 (앞 공백 문자열은 리소스에서 공백이 지워짐, 리뷰 1). 오늘 클리어면 이미 받은 말투
                val text = when {
                    impact.rewardPoint == null ->
                        stringResource(if (impact.todayCleared) R.string.game_quest_streak_today else R.string.game_quest_streak_if_clear, days)
                    impact.todayCleared -> stringResource(R.string.game_quest_streak_today_reward, days, impact.rewardPoint)
                    else -> stringResource(R.string.game_quest_streak_if_clear_reward, days, impact.rewardPoint)
                }
                val at = text.indexOf(days)
                append(text)
                if (at >= 0) addStyle(SpanStyle(color = Flame, fontWeight = FontWeight.Black), at, at + days.length)
            },
            style = MaterialTheme.typography.bodyLarge,
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
        QuestContent(
            QuestUiState.Loaded(
                TodayMission("회전 깜빡이 완벽", MissionStatus.ACHIEVED, 3, 3, 3, rewardPoint = 20),
                StreakImpact(days = 7, todayCleared = true, rewardPoint = 50),
                celebrate = true,
            ),
            onBack = {},
        )
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun QuestNarrowPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        QuestContent(
            QuestUiState.Loaded(
                TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 2, 1, 3, rewardPoint = 20),
                StreakImpact(days = 7, todayCleared = false, rewardPoint = 50),
            ),
            onBack = {},
        )
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun QuestFailedNoStreakPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        QuestContent(QuestUiState.Loaded(TodayMission("회전 깜빡이 완벽", MissionStatus.FAILED, 2, 1, 3, rewardPoint = 20)), onBack = {})
    }
}
