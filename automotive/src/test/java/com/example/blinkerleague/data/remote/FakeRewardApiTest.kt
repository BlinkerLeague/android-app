package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.TripEventDto
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import com.example.blinkerleague.data.remote.dto.TurnPayload
import com.example.blinkerleague.data.repository.RewardRepository
import com.example.blinkerleague.domain.model.PointReason
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** 서버 plan.md 4.2·4.3·4.5 규칙을 흉내 내는지 */
class FakeRewardApiTest {
    private val zone = ZoneId.of("Asia/Seoul")
    // 2026-11-09(월) 10:00
    private var now = ZonedDateTime.of(2026, 11, 9, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val trips = FakeTripApi()
    private val missions = FakeMissionApi(trips, { now }, zone)
    private val repository = RewardRepository(FakeRewardApi(trips, missions, { now }, zone))
    private var seq = 0

    private suspend fun trip(day: Int, hour: Int, km: Double, vararg signalOn: Boolean) {
        val id = "t${++seq}"
        val end = "2026-11-%02dT%02d:30:00+09:00".format(day, hour)
        trips.uploadTrip(
            TripUploadRequest(
                id, "2026-11-%02dT%02d:00:00+09:00".format(day, hour), end, km, listOf("TURN_SIGNAL"),
                signalOn.mapIndexed { i, on -> TripEventDto("$id-$i", "TURN", end, Json.encodeToJsonElement(TurnPayload("LEFT", on))) },
            ),
        )
    }

    @Test
    fun `운행 적립은 min(거리,50) × 평점 ÷ 100 내림, 미션 달성 20P`() = runTest {
        trip(9, 8, 12.3, true, true, false) // 67점 → floor(12.3 × 67 / 100) = 8
        trip(9, 9, 80.0, true, true, true) // 100점, 50km로 자름 → 50, 미션 달성 +20
        val wallet = repository.wallet()!!
        assertEquals(78, wallet.balance)
        assertEquals(78, wallet.todayEarned)
        assertEquals(150, wallet.dailyCap)
        assertEquals(listOf(PointReason.TRIP, PointReason.DAILY_MISSION, PointReason.TRIP).toSet(), wallet.entries.map { it.reason }.toSet())
        val first = wallet.entries.last()
        assertEquals(12.3, first.distanceKm!!, 0.0)
        assertEquals(67, first.score)
    }

    @Test
    fun `하루 상한 150P에서 잘림, 운행별 적립(earnedPoints)도 상한 적용 후`() = runTest {
        repeat(4) { trip(9, 6 + it, 50.0, true) } // 50P × 4 = 200 → 150
        assertEquals(150, repository.wallet()!!.balance)
        val api = FakeRewardApi(trips, missions, { now }, zone)
        assertEquals(listOf(50, 50, 50, 0), (1..4).map { api.earnedPointsFor("t$it") })
        assertEquals(null, api.earnedPointsFor("없는 운행"))
    }

    @Test
    fun `연속 달성 - 오늘 달성 전에는 어제부터 셈, 7일째 50P와 배지`() = runTest {
        for (day in 2..8) trip(day, 8, 5.0, true, true, true) // 11.2 ~ 11.8 매일 달성
        val streak = repository.streak()!!
        assertEquals(7, streak.days)
        assertFalse(streak.todayAchieved)
        assertTrue(repository.wallet()!!.entries.any { it.reason == PointReason.STREAK_7 })
        val badges = repository.badges()!!.associateBy { it.code }
        assertTrue(badges.getValue("FIRST_TRIP").earned)
        assertTrue(badges.getValue("STREAK_7").earned)
        assertFalse(badges.getValue("SIGNAL_100").earned)
    }

    @Test
    fun `주간 리포트 - 지난주 깜빡이 평균과 그 전 주 비교, 다음 주 미션, 이번 주는 400`() = runTest {
        trip(3, 8, 5.0, true, false) // 지난주(W45) 50점
        trip(4, 8, 5.0, true, true) // 지난주 100점 → 평균 75
        val report = repository.weeklyReport()!!
        assertEquals("2026-W45", report.week)
        assertEquals(75, report.metrics.single().thisWeek)
        assertEquals("TURN_SIGNAL", report.weakestMetric)
        assertEquals("2026-W46", report.nextMission!!.week)
        assertEquals("회전 깜빡이 주간 90점", report.nextMission!!.title)
        assertEquals(null, repository.weeklyReport("2026-W46")) // 아직 끝나지 않은 주
    }
}
