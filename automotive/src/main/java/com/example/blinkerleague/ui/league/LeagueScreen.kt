package com.example.blinkerleague.ui.league

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

@Composable
fun LeagueContent(state: LeagueUiState) {
    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        when (state) {
            LeagueUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            LeagueUiState.Unavailable -> Text(
                stringResource(R.string.league_unavailable),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            is LeagueUiState.Loaded -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MyLeaguePanel(state, Modifier.weight(1f).fillMaxHeight())
                if (state.top.isNotEmpty()) RankingPanel(state.top, Modifier.weight(1f).fillMaxHeight())
                RegionsPanel(state.regions, Modifier.weight(0.8f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun MyLeaguePanel(state: LeagueUiState.Loaded, modifier: Modifier) {
    Panel(modifier) {
        Label(stringResource(R.string.league_season, state.month, state.daysLeft))
        val my = state.my
        if (my == null) {
            Text(stringResource(R.string.league_no_region), style = MaterialTheme.typography.bodyLarge)
            return@Panel
        }
        Text(my.name, style = MaterialTheme.typography.headlineMedium)
        Label(stringResource(R.string.league_my_score))
        Text(
            my.myScore?.toString() ?: "—",
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 72.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        if (my.myScore != null) {
            Text(
                stringResource(R.string.league_score_breakdown, my.averageScore?.toString() ?: "—", my.points),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            when {
                my.rankPublic && my.myRank != null -> stringResource(R.string.league_rank, my.myRank, my.participants)
                my.rankPublic -> stringResource(R.string.league_not_ranked, my.participants)
                else -> stringResource(R.string.league_rank_hidden, state.rule.minParticipants, my.participants)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
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
            Text(stringResource(R.string.league_championships, titles), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RankingPanel(top: List<LeagueEntry>, modifier: Modifier) {
    Panel(modifier) {
        Label(stringResource(R.string.league_ranking))
        LazyColumn {
            items(top) { e ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.rank}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 20.dp))
                    Text(
                        e.nickname,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (e.me) FontWeight.Bold else FontWeight.Normal,
                        color = if (e.me) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${e.score}", style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

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
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun LeaguePreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        LeagueContent(
            LeagueUiState.Loaded(
                11, 21,
                MyLeague("서울 서북권", 24, true, 3, 128, true, 62.4, 12, emptyList(), averageScore = 88, points = 40),
                listOf(LeagueEntry(1, "안전왕", 145, false), LeagueEntry(2, "깜빡깜빡", 131, false), LeagueEntry(3, "blinker", 128, true)),
                listOf(LeagueRegion("서울 동남권", 89, 1), LeagueRegion("서울 서북권", 86, 2)),
                listOf(Championship("2026-10", "서울 서북권")),
            ),
        )
    }
}
