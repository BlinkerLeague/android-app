package com.example.blinkerleague.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.example.blinkerleague.ui.common.game.RevealClock
import com.example.blinkerleague.ui.common.game.rememberRevealClock
import com.example.blinkerleague.ui.common.game.reveal
import com.example.blinkerleague.ui.common.game.skipRevealOnTap
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import com.example.blinkerleague.ui.theme.OnWarningContainer
import com.example.blinkerleague.ui.theme.SuccessContainer
import com.example.blinkerleague.ui.theme.WarningContainer
import kotlinx.coroutines.launch
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

/** 진입 연출 (ms): 타임라인 막대 0~1200, 카드는 200부터 150 간격으로 옆에서 등장 (앞 다섯 장까지만 늦춤) */
private object PlaysMotion {
    const val TIMELINE = 1_200
    const val CARD = 200
    const val CARD_GAP = 150
    const val TOTAL = 1_400
}

/** 타임라인 점의 터치 영역 (주행 안전 원칙: 64dp 이상) */
private val DotTouch = 64.dp

@Composable
fun PlaysContent(state: PlaysUiState, onBack: () -> Unit, onOpenCard: () -> Unit = {}) {
    val clock = rememberRevealClock(PlaysMotion.TOTAL)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().skipRevealOnTap(clock).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!state.loading) Header(state, Modifier.fillMaxWidth().reveal(clock, 0))
        Panel(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (!state.loading && state.plays.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.plays_empty), style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    if (!state.loading) {
                        Timeline(state, clock, onSelect = { index -> scope.launch { listState.animateScrollToItem(index) } })
                    }
                    LazyRow(
                        Modifier.weight(1f).fillMaxWidth(),
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(state.plays) { index, play ->
                            PlayCardItem(
                                index = index,
                                play = play,
                                onClick = onOpenCard,
                                modifier = Modifier
                                    .widthIn(min = 200.dp)
                                    .fillParentMaxWidth(0.24f)
                                    .fillMaxHeight()
                                    .reveal(clock, PlaysMotion.CARD + minOf(index, 4) * PlaysMotion.CARD_GAP, fromX = 40.dp, fromY = 0.dp),
                            )
                        }
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
private fun Header(state: PlaysUiState, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.plays_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.plays_header, state.startedAt, state.endedAt, state.distanceKm, state.plays.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val description = stringResource(R.string.result_goals, state.goals, state.conceded)
            Row(Modifier.clearAndSetSemantics { contentDescription = description }) {
                val style = MaterialTheme.typography.headlineMedium
                Text(state.goals.toString(), style = style, color = OnSuccessContainer)
                Text(" : ", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.conceded.toString(), style = style, color = OnWarningContainer)
            }
        }
    }
}

/** 출발 ~ 도착 막대 위에 플레이 위치(시각 비율)대로 점. 점을 누르면 그 카드로 이동 */
@Composable
private fun Timeline(state: PlaysUiState, clock: RevealClock, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = MaterialTheme.typography.labelMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(stringResource(R.string.plays_timeline_start, state.startedAt), style = style, color = color)
            Text(stringResource(R.string.plays_timeline_end, state.endedAt), style = style, color = color)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(DotTouch)) {
            val track = Modifier.align(Alignment.Center).padding(horizontal = DotTouch / 2).fillMaxWidth().height(6.dp)
            Box(track.clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                Box(
                    Modifier.fillMaxWidth(clock.progress(0, PlaysMotion.TIMELINE)).fillMaxHeight()
                        .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                )
            }
            val travel = maxWidth - DotTouch
            state.plays.forEachIndexed { index, play ->
                val (ring, dot) = if (play.goal) SuccessContainer to OnSuccessContainer else WarningContainer to OnWarningContainer
                val description = stringResource(R.string.plays_dot_description, index + 1, play.time)
                Box(
                    Modifier
                        .offset(x = travel * play.position)
                        .size(DotTouch)
                        .clip(CircleShape)
                        .clickable { onSelect(index) }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(22.dp).border(5.dp, ring, CircleShape).padding(5.dp).clip(CircleShape).background(dot))
                }
            }
        }
    }
}

@Composable
private fun PlayCardItem(index: Int, play: PlayCard, onClick: () -> Unit, modifier: Modifier) {
    // 득점 파랑 · 실점 주황 (색만으로 구분하지 않도록 글자도 함께)
    val (container, content) = if (play.goal) SuccessContainer to OnSuccessContainer else WarningContainer to OnWarningContainer
    val label = when (play.direction) {
        TurnDirection.LEFT -> if (play.goal) R.string.plays_left_goal else R.string.plays_left_conceded
        TurnDirection.RIGHT -> if (play.goal) R.string.plays_right_goal else R.string.plays_right_conceded
    }
    // 카드를 누르면 깜빡이 항목 상세 (SCR-02b)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 200.dp, max = 380.dp),
        color = container,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, content),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.plays_card_label, index + 1, play.time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(if (play.direction == TurnDirection.LEFT) "↰" else "↱", fontSize = 64.sp, color = content)
            Text(
                stringResource(if (play.goal) R.string.plays_goal_word else R.string.plays_miss_word),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = content,
            )
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(if (play.goal) R.string.plays_signal_on else R.string.plays_signal_off),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val PreviewPlays = PlaysUiState(
    loading = false, startedAt = "08:10", endedAt = "08:42", distanceKm = 14.2, goals = 3, conceded = 1,
    plays = listOf(
        PlayCard("08:15", TurnDirection.LEFT, true, position = 0.16f),
        PlayCard("08:23", TurnDirection.RIGHT, false, position = 0.41f),
        PlayCard("08:30", TurnDirection.RIGHT, true, position = 0.63f),
        PlayCard("08:38", TurnDirection.LEFT, true, position = 0.88f),
        PlayCard("08:41", TurnDirection.LEFT, true, position = 0.97f),
    ),
)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun PlaysPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) { PlaysContent(PreviewPlays, onBack = {}) }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun PlaysNarrowPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) { PlaysContent(PreviewPlays, onBack = {}) }
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
