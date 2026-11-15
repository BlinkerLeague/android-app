package com.example.blinkerleague.ui.quest

import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.TodayMission

/**
 * 퀘스트 다음 행동 안내 (홈 · 퀘스트 화면).
 * 퀘스트는 운행 1건 = 1라운드로 판정하고 다음 운행은 0부터 다시 셈(서버 D25). 그래서 "한 번 더 회전하면"이 아니라 "다음 경기에서 회전 N번 모두"
 */
sealed interface QuestHint {
    data object Cleared : QuestHint

    /** 다음 경기에서 회전 [requiredTurns]번 모두 득점 */
    data class NextTrip(val requiredTurns: Int) : QuestHint

    companion object {
        fun of(mission: TodayMission): QuestHint =
            if (mission.status == MissionStatus.ACHIEVED) Cleared else NextTrip(mission.requiredTurns)
    }
}
