package com.example.blinkerleague.ui.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.blinkerleague.R
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import com.example.blinkerleague.ui.theme.OnWarningContainer
import com.example.blinkerleague.ui.theme.SuccessContainer
import com.example.blinkerleague.ui.theme.WarningContainer

/**
 * 오늘의 미션 카드. 결과 화면(작게)과 미션 화면(크게)에서 같이 씀.
 * @param large true면 제목·안내 문구를 크게 보여줌
 */
@Composable
fun MissionCard(mission: TodayMission, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(if (large) 32.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (large) 20.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.mission_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    mission.title,
                    style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                )
            }
            StatusChip(mission.status)
        }
        val progress = if (mission.requiredTurns > 0) {
            (mission.successTurns.toFloat() / mission.requiredTurns).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (large) 16.dp else 10.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = if (mission.status == MissionStatus.FAILED) OnWarningContainer else MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.mission_progress, minOf(mission.successTurns, mission.requiredTurns), mission.requiredTurns),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (large) {
            Text(
                stringResource(
                    when (mission.status) {
                        MissionStatus.IN_PROGRESS -> R.string.mission_hint_in_progress
                        MissionStatus.ACHIEVED -> R.string.mission_hint_achieved
                        MissionStatus.FAILED -> R.string.mission_hint_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(status: MissionStatus) {
    val (container, content, text) = when (status) {
        MissionStatus.IN_PROGRESS -> Triple(
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.onSurface,
            R.string.mission_status_in_progress,
        )
        MissionStatus.ACHIEVED -> Triple(SuccessContainer, OnSuccessContainer, R.string.mission_status_achieved)
        MissionStatus.FAILED -> Triple(WarningContainer, OnWarningContainer, R.string.mission_status_failed)
    }
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Preview(widthDp = 600)
@Composable
private fun MissionCardPreview() {
    BlinkerLeagueTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MissionCard(TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 2, 2, 3))
                MissionCard(TodayMission("회전 깜빡이 완벽", MissionStatus.FAILED, 2, 1, 3), large = true)
            }
        }
    }
}
