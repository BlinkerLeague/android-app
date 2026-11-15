package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.MissionApi
import com.example.blinkerleague.data.remote.dto.MissionConditionDto
import com.example.blinkerleague.data.remote.dto.MissionProgressDto
import com.example.blinkerleague.data.remote.dto.TodayMissionResponse
import com.example.blinkerleague.domain.model.ConditionType
import com.example.blinkerleague.domain.model.MissionCondition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MissionRepositoryTest {
    private fun repository(response: TodayMissionResponse) = MissionRepository(
        object : MissionApi {
            override suspend fun todayMission() = ApiResult.Success(response)
        },
    )

    private val base = TodayMissionResponse(1, "회전 깜빡이 완벽", "2026-11-08", "FAILED", MissionProgressDto(2, 1, 3))

    @Test
    fun `서버 조건·보상을 그대로 씀, 모르는 조건 종류는 버림`() = runTest {
        val mission = repository(
            base.copy(
                rewardPoint = 20,
                conditions = listOf(
                    MissionConditionDto("TURN_COUNT", 2, 3),
                    MissionConditionDto("SIGNAL_RATE", 50, 100),
                    MissionConditionDto("NEW_KIND", 1, 1),
                ),
            ),
        ).todayMission()!!
        assertEquals(20, mission.rewardPoint)
        assertEquals(
            listOf(MissionCondition(ConditionType.TURN_COUNT, 2, 3), MissionCondition(ConditionType.SIGNAL_RATE, 50, 100)),
            mission.conditions,
        )
    }

    @Test
    fun `서버가 조건을 안 주면 회전 수·사용률로 만듦`() = runTest {
        val mission = repository(base).todayMission()!!
        assertNull(mission.rewardPoint)
        assertEquals(listOf(2 to 3, 50 to 100), mission.conditions.map { it.current to it.target })
        assertEquals(listOf(false, false), mission.conditions.map { it.met })
    }

    @Test
    fun `퀘스트 회차 키는 서버 missionId와 periodKey로 만든다`() = runTest {
        // given
        val response = base.copy(missionId = 7, periodKey = "2026-11-15")

        // when
        val mission = repository(response).todayMission()!!

        // then
        assertEquals("7:2026-11-15", mission.periodId)
    }
}
