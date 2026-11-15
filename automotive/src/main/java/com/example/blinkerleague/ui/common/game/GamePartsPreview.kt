package com.example.blinkerleague.ui.common.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.Gold
import com.example.blinkerleague.ui.theme.gradeColor

@Preview(widthDp = 1280, heightDp = 300)
@Composable
private fun GamePartsPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        val clock = rememberRevealClock(totalMs = 3_000)
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GradeThreshold.DEFAULT.forEach {
                    GradeStamp(it.grade, gradeColor(it.grade, GradeThreshold.DEFAULT), clock, 0, Modifier.weight(1f).height(140.dp))
                }
                ProgressRing(0.66f, Gold, MaterialTheme.colorScheme.outlineVariant, Modifier.weight(1f).height(140.dp))
                StreakFlame(Modifier.weight(0.6f).height(100.dp))
                HatchedBox(Modifier.weight(1f).fillMaxWidth().height(16.dp))
            }
            Confetti(clock, startMs = 0)
        }
    }
}
