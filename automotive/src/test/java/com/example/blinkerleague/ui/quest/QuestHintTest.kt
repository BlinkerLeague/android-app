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

    @Test
    fun `클리어 전이면 다음 경기에서 회전 N번 모두 - 남은 횟수가 아니라 필요한 회전 수 전체`() {
        // given
        // 다음 운행은 0부터 다시 셈(서버 D25): 진행 중 2/3이어도 다음 경기에선 3번 모두
        val inProgress = TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 2, 2, 3)
        val failed = TodayMission("회전 깜빡이 완벽", MissionStatus.FAILED, 3, 1, 3)

        // when
        val hints = listOf(inProgress, failed).map(QuestHint::of)

        // then
        assertEquals(listOf(QuestHint.NextTrip(3), QuestHint.NextTrip(3)), hints)
    }
}
