package com.example.blinkerleague.ui.wallet

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.example.blinkerleague.domain.model.PointReason
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import java.time.ZoneId

private val WalletViewModelFactory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container
        WalletViewModel(container.rewardRepository::wallet, ZoneId.systemDefault())
    }
}

@Composable
fun WalletScreen(viewModel: WalletViewModel = viewModel(factory = WalletViewModelFactory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    WalletContent(state)
}

@Composable
fun WalletContent(state: WalletUiState) {
    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        when (state) {
            WalletUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            WalletUiState.Unavailable -> Text(
                stringResource(R.string.wallet_unavailable),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center),
            )
            is WalletUiState.Loaded -> Loaded(state)
        }
    }
}

@Composable
private fun Loaded(state: WalletUiState.Loaded) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(0.8f).fillMaxHeight()) {
            Text(stringResource(R.string.wallet_balance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.wallet_points, state.balance),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 72.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.wallet_today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val cap = state.dailyCap
            Text(
                if (cap != null) stringResource(R.string.wallet_today_of_cap, state.todayEarned, cap) else stringResource(R.string.wallet_points, state.todayEarned),
                style = MaterialTheme.typography.titleLarge,
            )
            if (cap != null && cap > 0) {
                LinearProgressIndicator(
                    progress = { (state.todayEarned.toFloat() / cap).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp),
                )
                if (state.todayEarned >= cap) {
                    Text(stringResource(R.string.wallet_cap_reached), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Panel(Modifier.weight(1.2f).fillMaxHeight()) {
            Text(stringResource(R.string.wallet_history), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.entries.isEmpty()) {
                Text(stringResource(R.string.wallet_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    items(state.entries) { row ->
                        EntryRow(row)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(row: WalletRow) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(
                    when (row.reason) {
                        PointReason.TRIP -> R.string.wallet_reason_trip
                        PointReason.DAILY_MISSION -> R.string.wallet_reason_mission
                        PointReason.STREAK_7 -> R.string.wallet_reason_streak
                        PointReason.OTHER -> R.string.wallet_reason_other
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            val detail = if (row.reason == PointReason.TRIP && row.distanceKm != null && row.score != null) {
                stringResource(R.string.wallet_trip_detail, row.time, row.distanceKm, row.score)
            } else {
                row.time
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.wallet_amount, row.amount),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
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
private fun WalletPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        WalletContent(
            WalletUiState.Loaded(
                balance = 1240, todayEarned = 62, dailyCap = 150,
                entries = listOf(
                    WalletRow("11.09 08:40", PointReason.DAILY_MISSION, 20, null, null),
                    WalletRow("11.09 08:40", PointReason.TRIP, 10, 12.3, 86),
                ),
            ),
        )
    }
}
