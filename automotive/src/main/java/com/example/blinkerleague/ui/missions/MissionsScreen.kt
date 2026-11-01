package com.example.blinkerleague.ui.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme

private val MissionsViewModelFactory = viewModelFactory {
    initializer { MissionsViewModel((this[APPLICATION_KEY] as BlinkerLeagueApplication).container.missionRepository) }
}

/** 오늘의 미션 화면 (MVP는 1종). 정차 상태에서만 여는 화면 */
@Composable
fun MissionsScreen(
    onBack: () -> Unit,
    viewModel: MissionsViewModel = viewModel(factory = MissionsViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MissionsContent(uiState, onBack)
}

@Composable
fun MissionsContent(uiState: MissionsUiState, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (uiState) {
                MissionsUiState.Loading -> CircularProgressIndicator()
                MissionsUiState.Unavailable -> Text(
                    stringResource(R.string.mission_unavailable),
                    style = MaterialTheme.typography.titleLarge,
                )
                is MissionsUiState.Loaded -> MissionCard(uiState.mission, large = true)
            }
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
        ) {
            Text(stringResource(R.string.mission_back), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(widthDp = 960, heightDp = 720)
@Composable
private fun MissionsPreview() {
    BlinkerLeagueTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MissionsContent(
                MissionsUiState.Loaded(TodayMission("회전 깜빡이 완벽", MissionStatus.ACHIEVED, 3, 3, 3)),
                onBack = {},
            )
        }
    }
}
