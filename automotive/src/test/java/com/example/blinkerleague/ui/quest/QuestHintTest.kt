package com.example.blinkerleague.ui.quest

import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.TodayMission
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestHintTest {

    @Test
    fun `퀘스트를 클리어했으면 클리어 안내`() {
        // given
        val mission = TodayMission("회전 깜빡이 완벽", MissionStatus.ACHIEVED, 3, 3, 3)

        // when
        val hint = QuestHint.of(mission)

        // then
        assertEquals(QuestHint.Cleared, hint)
    }
}
