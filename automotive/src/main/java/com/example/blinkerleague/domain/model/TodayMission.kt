package com.example.blinkerleague.domain.model

enum class MissionStatus { IN_PROGRESS, ACHIEVED, FAILED }

/** 오늘의 미션 (MVP: "회전 깜빡이 완벽" 1종) */
data class TodayMission(
    val title: String,
    val status: MissionStatus,
    val turns: Int,
    val successTurns: Int,
    val requiredTurns: Int,
    /** 달성 보상 포인트 (지급은 P1 포인트 원장). 서버가 안 주면 null */
    val rewardPoint: Int? = null,
    /** 조건별 진행도 (SCR-03). 서버가 안 주면 회전 수·사용률로 만듦 */
    val conditions: List<MissionCondition> = MissionCondition.derive(turns, successTurns, requiredTurns),
    /** 이 퀘스트 한 회차(서버 missionId + periodKey)를 가리키는 키. 클리어 축하를 회차당 한 번만 보여 줄 때 씀. 모르면 null */
    val periodId: String? = null,
)

enum class ConditionType {
    /** 회전 N회 이상 */
    TURN_COUNT,

    /** 깜빡이 사용률(%) */
    SIGNAL_RATE,
}

data class MissionCondition(val type: ConditionType, val current: Int, val target: Int) {
    val met: Boolean get() = current >= target

    companion object {
        /** 서버 `conditions`가 없을 때: 회전 수와 사용률(회전이 없으면 0%) */
        fun derive(turns: Int, successTurns: Int, requiredTurns: Int): List<MissionCondition> = listOf(
            MissionCondition(ConditionType.TURN_COUNT, turns, requiredTurns),
            MissionCondition(ConditionType.SIGNAL_RATE, if (turns == 0) 0 else successTurns * 100 / turns, 100),
        )
    }
}
