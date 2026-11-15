package com.example.blinkerleague.ui.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blinkerleague.R
import com.example.blinkerleague.domain.model.ConditionType
import com.example.blinkerleague.domain.model.MissionCondition
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.ui.common.game.ProgressRing
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer
import com.example.blinkerleague.ui.theme.OnWarningContainer
import com.example.blinkerleague.ui.theme.SuccessContainer
import com.example.blinkerleague.ui.theme.WarningContainer

/**
 * 오늘의 퀘스트 카드 (홈). 진행 링(득점한 회전 / 필요한 회전) + 조건 체크 + 다음 행동 한 줄([QuestHint]).
 * @param fill 링을 채우는 연출 진행도(0..1). 호출하는 쪽이 진입 연출 시계로 넘김
 */
@Composable
fun MissionCard(mission: TodayMission, modifier: Modifier = Modifier, fill: Float = 1f) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.mission_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(mission.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
            StatusChip(mission.status)
        }
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            QuestRing(mission, fill, Modifier.weight(0.42f).fillMaxHeight().heightIn(max = 170.dp).padding(vertical = 4.dp))
            Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                mission.conditions.forEach { ConditionCheck(it) }
                Text(
                    questHintText(QuestHint.of(mission)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 진행 링: 가운데 "2/3 · 회전" (득점한 회전 / 필요한 회전) */
@Composable
private fun QuestRing(mission: TodayMission, fill: Float, modifier: Modifier) {
    val progress = if (mission.requiredTurns > 0) (mission.successTurns.toFloat() / mission.requiredTurns).coerceIn(0f, 1f) else 0f
    val color = when (mission.status) {
        MissionStatus.ACHIEVED -> OnSuccessContainer
        MissionStatus.FAILED -> OnWarningContainer
        MissionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        // 링은 칸의 높이 · 폭 중 작은 쪽에 맞춤 (좁은 화면에서는 작아짐)
        ProgressRing(progress * fill, color, MaterialTheme.colorScheme.outlineVariant, strokeWidth = 14.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.quest_progress_count, minOf(mission.successTurns, mission.requiredTurns), mission.requiredTurns),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
            )
            Text(stringResource(R.string.game_quest_ring_unit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 조건 한 줄: 달성하면 성공색 체크, 아니면 빈 원 */
@Composable
private fun ConditionCheck(condition: MissionCondition) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CheckMark(condition.met, Modifier.size(24.dp))
        Text(conditionLabel(condition), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
        Text(
            conditionProgress(condition),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = if (condition.met) OnSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 체크 원. 달성이면 성공색으로 채우고 ✓, 아니면 테두리만 */
@Composable
internal fun CheckMark(met: Boolean, modifier: Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (met) OnSuccessContainer else MaterialTheme.colorScheme.surface)
            .border(2.dp, if (met) OnSuccessContainer else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (met) {
            Text(
                stringResource(R.string.game_check),
                color = SuccessContainer,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun conditionLabel(condition: MissionCondition): String = when (condition.type) {
    ConditionType.TURN_COUNT -> stringResource(R.string.quest_condition_turns, condition.target)
    ConditionType.SIGNAL_RATE -> stringResource(R.string.quest_condition_rate, condition.target)
}

@Composable
internal fun conditionProgress(condition: MissionCondition): String = when (condition.type) {
    ConditionType.TURN_COUNT -> stringResource(R.string.quest_progress_count, condition.current, condition.target)
    ConditionType.SIGNAL_RATE -> stringResource(R.string.quest_progress_rate, condition.current)
}

/** 다음 행동 한 줄. 다음 운행은 0부터 다시 세므로(서버 D25) "한 번 더"가 아니라 "다음 경기에서 회전 N번 모두" */
@Composable
internal fun questHintText(hint: QuestHint): String = when (hint) {
    QuestHint.Cleared -> stringResource(R.string.mission_hint_achieved)
    is QuestHint.NextTrip -> stringResource(R.string.game_quest_hint_next, hint.requiredTurns)
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
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Preview(widthDp = 600, heightDp = 640)
@Composable
private fun MissionCardPreview() {
    BlinkerLeagueTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MissionCard(TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 2, 2, 3), Modifier.weight(1f))
                MissionCard(TodayMission("회전 깜빡이 완벽", MissionStatus.ACHIEVED, 3, 3, 3), Modifier.weight(1f))
            }
        }
    }
}
