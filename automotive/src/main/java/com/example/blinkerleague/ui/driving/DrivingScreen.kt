package com.example.blinkerleague.ui.driving

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.blinkerleague.R
import com.example.blinkerleague.domain.detection.GuardState
import com.example.blinkerleague.service.RecordingStatus
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme

/**
 * 주행 중 화면 (DRV). 점수·숫자·버튼을 두지 않음 (CLAUDE.md 주행 안전 원칙, screen-plan 3장).
 * Surface가 터치를 받아 아래 정차 화면으로 넘기지 않음.
 */
@Composable
fun DrivingScreen(state: DrivingGuardUiState, modifier: Modifier = Modifier) {
    val (title, body, active) = when {
        state.guard == null -> Triple(R.string.drv_checking, null, false)
        state.guard == GuardState.VEHICLE_UNKNOWN -> Triple(R.string.drv_vehicle_unknown, R.string.drv_vehicle_unknown_body, false)
        state.recordingStatus == RecordingStatus.OFF -> Triple(R.string.drv_recording_off, R.string.drv_recording_off_body, false)
        else -> Triple(R.string.drv_recording, R.string.drv_recording_body, true)
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RecordingMark(active = active)
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 기록 중이면 강조색 점, 아니면 회색 테두리만 */
@Composable
private fun RecordingMark(active: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(96.dp)) {
        drawCircle(color = if (active) accent else outline, style = Stroke(width = 8.dp.toPx()))
        if (active) drawCircle(color = accent, radius = size.minDimension / 4)
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun DrivingScreenRecordingPreview() = BlinkerLeagueTheme {
    DrivingScreen(DrivingGuardUiState(GuardState.DRIVING, RecordingStatus.RECORDING))
}

@Preview(widthDp = 640, heightDp = 720)
@Composable
private fun DrivingScreenUnknownPreview() = BlinkerLeagueTheme {
    DrivingScreen(DrivingGuardUiState(GuardState.VEHICLE_UNKNOWN, RecordingStatus.WAITING))
}
