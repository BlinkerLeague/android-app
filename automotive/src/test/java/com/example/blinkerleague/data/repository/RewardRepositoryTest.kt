package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.RewardApi
import com.example.blinkerleague.data.remote.dto.BadgeDto
import com.example.blinkerleague.data.remote.dto.PointsResponse
import com.example.blinkerleague.data.remote.dto.StreakResponse
import com.example.blinkerleague.data.remote.dto.WeeklyReportResponse
import com.example.blinkerleague.domain.model.PointReason
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 로컬 서버(develop 5d1ad9c)의 실제 응답 그대로 (2026-11-09) */
class RewardRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val points = """{"balance":32,"todayEarned":32,"dailyCap":150,"entries":[{"amount":20,"reason":"DAILY_MISSION","description":null,"createdAt":"2026-11-09T21:13:50.589919+09:00"},{"amount":12,"reason":"TRIP","description":{"distanceKm":12.3,"score":100},"createdAt":"2026-11-09T21:13:50.560524+09:00"}]}"""
    private val badges = """[{"code":"FIRST_TRIP","name":"첫 경기","condition":"점수가 난 첫 운행","earned":true,"awardedAt":"2026-11-09T21:13:50.596091+09:00"},{"code":"STREAK_7","name":"7일 연속","condition":"오늘의 미션 7일 연속 달성","earned":false,"awardedAt":null}]"""
    private val report = """{"week":"2026-W45","trips":0,"distanceKm":0.0,"metrics":[],"weakestMetric":null,"lastMission":null,"nextMission":null}"""

    private fun repository(fail: Boolean = false) = RewardRepository(object : RewardApi {
        override suspend fun points() = if (fail) ApiResult.HttpError(500) else ApiResult.Success(json.decodeFromString<PointsResponse>(points))
        override suspend fun streak() = ApiResult.Success(json.decodeFromString<StreakResponse>("""{"streakDays":1,"todayAchieved":true}"""))
        override suspend fun badges() = ApiResult.Success(json.decodeFromString<List<BadgeDto>>(badges))
        override suspend fun weeklyReport(week: String?) = ApiResult.Success(json.decodeFromString<WeeklyReportResponse>(report))
    })

    @Test
    fun `서버 응답을 화면용 모델로`() = runTest {
        val wallet = repository().wallet()!!
        assertEquals(32, wallet.balance)
        assertEquals(listOf(PointReason.DAILY_MISSION, PointReason.TRIP), wallet.entries.map { it.reason })
        assertEquals(12.3, wallet.entries[1].distanceKm!!, 0.0)
        assertTrue(wallet.entries[0].createdAtMs > 0)

        assertEquals(1, repository().streak()!!.days)
        val badgeList = repository().badges()!!
        assertTrue(badgeList[0].earned)
        assertNull(badgeList[1].awardedAtMs)

        val weekly = repository().weeklyReport()!!
        assertEquals("2026-W45", weekly.week)
        assertNull(weekly.nextMission)
    }

    @Test
    fun `받지 못하면 null`() = runTest {
        assertNull(repository(fail = true).wallet())
    }
}
