package com.example.blinkerleague.ui.achievements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.example.blinkerleague.ui.common.game.RevealClock
import com.example.blinkerleague.ui.common.game.StreakDots
import com.example.blinkerleague.ui.common.game.StreakFlame
import com.example.blinkerleague.ui.common.game.rememberRevealClock
import com.example.blinkerleague.ui.common.game.reveal
import com.example.blinkerleague.ui.common.game.skipRevealOnTap
import com.example.blinkerleague.ui.theme.Flame
import com.example.blinkerleague.ui.theme.Gold
import com.example.blinkerleague.ui.theme.GoldContainer
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

/** 진입 연출 (ms): 연승 카드 0 → 진열장 150 → 배지 300부터 60 간격. 정차 화면에서만, 한 번만, 탭하면 건너뜀 */
private object ShelfMotion {
    const val SHELF = 150
    const val BADGE = 300
    const val BADGE_GAP = 60
    const val MAX_BADGES = 12
    const val TOTAL = BADGE + BADGE_GAP * MAX_BADGES + 400
}

@Composable
fun AchievementsContent(state: AchievementsUiState, onOpenReport: () -> Unit) {
    val clock = rememberRevealClock(ShelfMotion.TOTAL)
    Box(Modifier.fillMaxSize().skipRevealOnTap(clock).padding(horizontal = 28.dp, vertical = 24.dp)) {
        when (state) {
            AchievementsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            AchievementsUiState.Unavailable -> Text(
                stringResource(R.string.achievements_unavailable),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center),
            )
            is AchievementsUiState.Loaded -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(0.75f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    state.streak?.let { StreakPanel(it, Modifier.weight(1f).fillMaxWidth().reveal(clock, 0)) }
                    OutlinedButton(onClick = onOpenReport, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Text(stringResource(R.string.report_open), style = MaterialTheme.typography.labelLarge)
                    }
                }
                ShelfPanel(state, clock, Modifier.weight(1.25f).fillMaxHeight().reveal(clock, ShelfMotion.SHELF))
            }
        }
    }
}

/** 연승 카드: 불꽃 · "N연승" · 오늘 달성 여부 · 주기 칸 진행 점 · 다음 보상 */
@Composable
private fun StreakPanel(streak: StreakCard, modifier: Modifier) {
    Panel(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.achievements_streak), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StreakFlame(Modifier.weight(1f, fill = false).heightIn(max = 96.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 64.sp)) { append(streak.days.toString()) }
                withStyle(SpanStyle(fontSize = 26.sp)) { append(stringResource(R.string.game_streak_unit)) }
            },
            fontWeight = FontWeight.Black,
            color = Flame,
            maxLines = 1,
        )
        Text(
            stringResource(if (streak.todayAchieved) R.string.achievements_today_done else R.string.achievements_today_todo),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        StreakDots(streakDotsFilled(streak.days, streak.period), streak.period, Modifier.fillMaxWidth().height(20.dp))
        Text(
            stringResource(R.string.achievements_reward_in, streak.daysToReward, streak.rewardPoint),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Gold,
            textAlign = TextAlign.Center,
        )
    }
}

/** 트로피 진열장: "획득 N / 전체 M" + 배지 그리드 (진행 막대 · 가장 가까운 트로피는 데이터가 없어 넣지 않음, plan-ui 결정 7) */
@Composable
private fun ShelfPanel(state: AchievementsUiState.Loaded, clock: RevealClock, modifier: Modifier) {
    Panel(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.achievements_shelf),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                modifier = Modifier.weight(1f),
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.Black)) {
                        append(stringResource(R.string.achievements_earned_count, state.earnedCount))
                    }
                    append(stringResource(R.string.achievements_total_count, state.badges.size))
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.badges.isEmpty()) {
            Text(stringResource(R.string.achievements_badges_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.badges) { i, badge ->
                val start = ShelfMotion.BADGE + ShelfMotion.BADGE_GAP * minOf(i, ShelfMotion.MAX_BADGES)
                BadgeItem(badge, Modifier.reveal(clock, start))
            }
        }
    }
}

/** 배지 하나. 획득: 색 아이콘 + 금 테두리 + 획득 날짜. 잠김: 회색 실루엣 + 자물쇠 + 서버 조건 문구 그대로 */
@Composable
private fun BadgeItem(badge: BadgeCard, modifier: Modifier) {
    val locked = !badge.earned
    Surface(
        modifier = modifier.heightIn(min = 196.dp),
        color = if (locked) MaterialTheme.colorScheme.background else GoldContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, if (locked) MaterialTheme.colorScheme.outlineVariant else Gold.copy(alpha = 0.6f)),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(if (locked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    BadgeIcon(badge.code, locked, Modifier.size(56.dp).alpha(if (locked) 0.7f else 1f))
                }
                if (locked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.achievements_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .padding(6.dp),
                    )
                }
            }
            Text(
                badge.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else Gold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                badge.condition,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!locked) {
                Text(
                    badge.awardedOn?.let { stringResource(R.string.achievements_earned_on, it) } ?: stringResource(R.string.achievements_earned),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Panel(
    modifier: Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = horizontalAlignment,
        ) { content() }
    }
}

private val PreviewState = AchievementsUiState.Loaded(
    StreakCard(5, false, 2),
    listOf(
        BadgeCard("첫 경기", "점수가 난 첫 운행", true, "11.09", "FIRST_TRIP"),
        BadgeCard("7일 연속", "오늘의 퀘스트 7일 연속 달성", false, null, "STREAK_7"),
        BadgeCard("깜빡이 100회", "깜빡이를 켠 회전 누적 100회", true, "11.12", "SIGNAL_100"),
        BadgeCard("시즌 우승", "지역 리그 시즌 1위", false, null, "SEASON_CHAMPION"),
        BadgeCard("새 배지", "서버에 새로 생긴 배지", false, null, "NEW_BADGE"),
    ),
    earnedCount = 2,
)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun AchievementsPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        AchievementsContent(PreviewState, onOpenReport = {})
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun AchievementsNarrowPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        AchievementsContent(PreviewState.copy(streak = StreakCard(7, true, 7, period = 7)), onOpenReport = {})
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun AchievementsNoStreakPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        AchievementsContent(AchievementsUiState.Loaded(null, emptyList()), onOpenReport = {})
    }
}
