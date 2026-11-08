package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.MissionApi
import com.example.blinkerleague.domain.model.ConditionType
import com.example.blinkerleague.domain.model.MissionCondition
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.TodayMission

class MissionRepository(private val api: MissionApi) {

    /** 오늘의 미션. 받지 못하면 null (화면에서는 미션 카드를 숨김) */
    suspend fun todayMission(): TodayMission? {
        val body = (api.todayMission() as? ApiResult.Success)?.body ?: return null
        val status = MissionStatus.entries.firstOrNull { it.name == body.status } ?: return null
        val conditions = body.conditions.mapNotNull { c ->
            ConditionType.entries.firstOrNull { it.name == c.type }?.let { MissionCondition(it, c.current, c.target) }
        }
        return TodayMission(
            title = body.title,
            status = status,
            turns = body.progress.turns,
            successTurns = body.progress.successTurns,
            requiredTurns = body.progress.requiredTurns,
            rewardPoint = body.rewardPoint,
            conditions = conditions.ifEmpty {
                MissionCondition.derive(body.progress.turns, body.progress.successTurns, body.progress.requiredTurns)
            },
        )
    }
}
