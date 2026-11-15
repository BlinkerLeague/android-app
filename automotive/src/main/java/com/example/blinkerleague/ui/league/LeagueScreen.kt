package com.example.blinkerleague.ui.league

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.blinkerleague.ui.common.game.RevealClock
import com.example.blinkerleague.ui.common.game.rememberRevealClock
import com.example.blinkerleague.ui.common.game.reveal
import com.example.blinkerleague.ui.common.game.skipRevealOnTap
import com.example.blinkerleague.ui.theme.Bronze
import com.example.blinkerleague.ui.theme.Flame
import com.example.blinkerleague.ui.theme.Gold
import com.example.blinkerleague.ui.theme.OnWarningContainer
import com.example.blinkerleague.ui.theme.Silver
import com.example.blinkerleague.ui.theme.SuccessContainer
import com.example.blinkerleague.ui.theme.WarningContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.blinkerleague.domain.model.Championship
import com.example.blinkerleague.domain.model.LeagueEntry
import com.example.blinkerleague.domain.model.LeagueRegion
import com.example.blinkerleague.domain.model.MyLeague
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import kotlin.math.roundToInt

private val LeagueViewModelFactory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container
        LeagueViewModel(container.leagueRepository::current, { container.config.league }, System::currentTimeMillis)
    }
}

@Composable
fun LeagueScreen(viewModel: LeagueViewModel = viewModel(factory = LeagueViewModelFactory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LeagueContent(state)
}

/** 진입 연출 (ms): 내 리그 0 → 순위표 150 → 내 행 600(옆에서 들어옴) → 지역 300. 정차 화면에서만, 한 번만, 탭하면 건너뜀 */
private object LeagueMotion {
    const val RANKING = 150
    const val REGIONS = 300
    const val MY_ROW = 600
    const val TOTAL = 1_100
}

@Composable
fun LeagueContent(state: LeagueUiState) {
    val clock = rememberRevealClock(LeagueMotion.TOTAL)
    Box(Modifier.fillMaxSize().skipRevealOnTap(clock).padding(horizontal = 28.dp, vertical = 24.dp)) {
        when (state) {
            LeagueUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            LeagueUiState.Unavailable -> Text(
                stringResource(R.string.league_unavailable),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            is LeagueUiState.Loaded -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MyLeaguePanel(state, Modifier.weight(1f).fillMaxHeight().reveal(clock, 0))
                if (state.top.isNotEmpty()) {
                    RankingPanel(state.top, clock, Modifier.weight(1.1f).fillMaxHeight().reveal(clock, LeagueMotion.RANKING))
                }
                RegionsPanel(state.regions, Modifier.weight(0.8f).fillMaxHeight().reveal(clock, LeagueMotion.REGIONS))
            }
        }
    }
}

/** 내 리그: 시즌 · D-day 칩, 큰 "N위"(순위가 없으면 기존 안내 문구), 리그 점수, 순위 자격. 리그 단계 · 순위 변동 · 승급까지는 데이터가 없어 넣지 않음(plan-ui 결정 6) */
@Composable
private fun MyLeaguePanel(state: LeagueUiState.Loaded, modifier: Modifier) {
    Panel(modifier, scrollable = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.league_season_month, state.month), Modifier.weight(1f))
            DdayChip(state.daysLeft)
        }
        val my = state.my
        if (my == null) {
            Text(stringResource(R.string.league_no_region), style = MaterialTheme.typography.bodyLarge)
            return@Panel
        }
        Text(my.name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
        if (my.rankPublic && my.myRank != null) {
            Label(stringResource(R.string.league_my_rank))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 88.sp)) { append(my.myRank.toString()) }
                    withStyle(SpanStyle(fontSize = 34.sp)) { append(stringResource(R.string.league_rank_unit)) }
                },
                fontWeight = FontWeight.Black,
                color = rankColor(my.myRank) ?: MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.league_rank_of, my.participants),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                if (my.rankPublic) {
                    stringResource(R.string.league_not_ranked, my.participants)
                } else {
                    stringResource(R.string.league_rank_hidden, state.rule.minParticipants, my.participants)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(stringResource(R.string.league_my_score))
            Text(
                my.myScore?.toString() ?: "—",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (my.myScore != null) {
            Text(
                stringResource(R.string.league_score_breakdown, my.averageScore?.toString() ?: "—", my.points),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            if (my.qualified) stringResource(R.string.league_qualified) else stringResource(R.string.league_qualify_rule, state.rule.minSeasonDistanceKm.roundToInt(), state.rule.minSeasonTrips),
            style = MaterialTheme.typography.bodyMedium,
            color = if (my.qualified) OnSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.league_season_progress, my.seasonKm, my.seasonTrips),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.championships.isNotEmpty()) {
            val titles = state.championships.map { stringResource(R.string.league_championship_item, it.seasonId, it.name) }.joinToString(", ")
            Text(stringResource(R.string.league_championships, titles), style = MaterialTheme.typography.bodyMedium, color = Gold)
        }
    }
}

/** 시즌 종료까지 남은 날 칩 (점멸 없음) */
@Composable
private fun DdayChip(daysLeft: Int) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(WarningContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.league_season_end), style = MaterialTheme.typography.labelMedium, color = OnWarningContainer)
        Text(
            stringResource(R.string.league_dday, daysLeft),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
            color = Flame,
        )
    }
}

/** 1~3위 금 · 은 · 동, 그 밖은 null */
private fun rankColor(rank: Int): Color? = when (rank) {
    1 -> Gold
    2 -> Silver
    3 -> Bronze
    else -> null
}

/** 순위표: 1~3위 메달 색, 내 행 강조(옆에서 들어옴). 승강 구역 · 순위 변동은 넣지 않음(plan-ui 결정 6) */
@Composable
private fun RankingPanel(top: List<LeagueEntry>, clock: RevealClock, modifier: Modifier) {
    Panel(modifier) {
        Label(stringResource(R.string.league_ranking))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(top) { e ->
                val medal = rankColor(e.rank)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (e.me) Modifier.reveal(clock, LeagueMotion.MY_ROW, fromX = 40.dp, fromY = 0.dp) else Modifier)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (e.me) SuccessContainer else Color.Transparent)
                        .then(if (e.me) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (medal != null) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(medal), contentAlignment = Alignment.Center) {
                                Text("${e.rank}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = MedalText)
                            }
                        } else {
                            Text(
                                "${e.rank}",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (e.me) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        if (e.me) stringResource(R.string.league_me, e.nickname) else e.nickname,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (e.me) FontWeight.Black else FontWeight.Normal,
                        color = if (e.me) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    Text(
                        "${e.score}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (e.me) MaterialTheme.colorScheme.primary else medal ?: MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private val MedalText = Color(0xFF1B2027)

@Composable
private fun RegionsPanel(regions: List<LeagueRegion>, modifier: Modifier) {
    Panel(modifier) {
        Label(stringResource(R.string.league_regions))
        if (regions.none { it.safetyIndex != null }) {
            Text(stringResource(R.string.league_regions_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        regions.forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.rank?.let { "$it" } ?: "—", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 16.dp))
                Text(r.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(r.safetyIndex?.toString() ?: "—", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Panel(modifier: Modifier, scrollable: Boolean = false, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

private val PreviewState = LeagueUiState.Loaded(
    11, 21,
    MyLeague("서울 서북권", 24, true, 3, 128, true, 62.4, 12, emptyList(), averageScore = 88, points = 40),
    listOf(
        LeagueEntry(1, "안전왕", 145, false),
        LeagueEntry(2, "깜빡깜빡", 131, false),
        LeagueEntry(3, "blinker", 128, true),
        LeagueEntry(4, "정속주행", 120, false),
        LeagueEntry(5, "조심조심", 117, false),
    ),
    listOf(LeagueRegion("서울 동남권", 89, 1), LeagueRegion("서울 서북권", 86, 2)),
    listOf(Championship("2026-10", "서울 서북권")),
)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun LeaguePreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        LeagueContent(PreviewState)
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun LeagueNarrowPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        LeagueContent(
            PreviewState.copy(
                my = PreviewState.my?.copy(myRank = 7),
                top = PreviewState.top.map { it.copy(me = false) } + LeagueEntry(7, "blinker", 101, true),
            ),
        )
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun LeagueRankHiddenPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        LeagueContent(
            PreviewState.copy(
                my = MyLeague("서울 서북권", 3, false, null, 96, false, 8.2, 2, emptyList(), averageScore = 90, points = 6),
                top = emptyList(),
                regions = emptyList(),
                championships = emptyList(),
            ),
        )
    }
}
