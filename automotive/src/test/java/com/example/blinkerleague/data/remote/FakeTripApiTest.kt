package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.PlayDto
import com.example.blinkerleague.data.remote.dto.PlaySummaryDto
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.data.remote.dto.TripEventDto
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import com.example.blinkerleague.data.remote.dto.TurnPayload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeTripApiTest {

    private val api = FakeTripApi()

    /** [signals]: 회전마다 깜빡이를 켰는지. 회전은 1분 간격, 일부러 역순으로 넣어 정렬을 확인 */
    private fun request(id: String, endedAt: String, distanceKm: Double, signals: List<Boolean>) = TripUploadRequest(
        tripId = id,
        startedAt = "2026-11-03T08:00:00+09:00",
        endedAt = endedAt,
        distanceKm = distanceKm,
        supported = listOf("TURN_SIGNAL", "STEERING_ANGLE"),
        events = signals.mapIndexed { i, on ->
            TripEventDto("$id-$i", "TURN", "2026-11-03T08:%02d:00+09:00".format(i + 1), Json.encodeToJsonElement(TurnPayload("LEFT", on)))
        }.reversed(),
    )

    @Test
    fun `점수 응답에 등급·득점실점·플레이가 시각순으로 들어감`() = runTest {
        api.uploadTrip(request("a", "2026-11-03T08:30:00+09:00", 5.0, listOf(true, true, false, true)))

        val result = (api.getScore("a") as ApiResult.Success).body
        assertEquals(ScoreStatus.SCORED, result.status)
        assertEquals(75, result.score)
        assertEquals("B", result.grade)
        assertEquals(PlaySummaryDto(goals = 3, conceded = 1, fouls = 0), result.summary)
        assertEquals(
            listOf("GOAL", "GOAL", "CONCEDED", "GOAL"),
            result.plays.map(PlayDto::result),
        )
        assertEquals(
            listOf("LEFT", "LEFT", "LEFT", "LEFT"),
            result.plays.map(PlayDto::direction),
        )
        assertEquals("2026-11-03T08:01:00+09:00", result.plays.first().occurredAt)
    }

    @Test
    fun `등급 경계 - 95는 S, 50 미만은 등급 없음`() = runTest {
        api.uploadTrip(request("s", "2026-11-03T08:30:00+09:00", 5.0, List(19) { true } + false)) // 95
        api.uploadTrip(request("low", "2026-11-03T08:31:00+09:00", 5.0, listOf(true, false, false))) // 33
        api.uploadTrip(request("none", "2026-11-03T08:32:00+09:00", 5.0, emptyList()))

        assertEquals("S", (api.getScore("s") as ApiResult.Success).body.grade)
        assertNull((api.getScore("low") as ApiResult.Success).body.grade)
        val noTurn = (api.getScore("none") as ApiResult.Success).body
        assertEquals(ScoreStatus.NO_TURN, noTurn.status)
        assertNull(noTurn.grade)
    }

    @Test
    fun `없는 운행은 404`() = runTest {
        assertEquals(ApiResult.HttpError(404), api.getScore("missing"))
    }

    @Test
    fun `최근 경기는 종료 시각 최신순으로 limit 만큼`() = runTest {
        api.uploadTrip(request("old", "2026-11-03T08:30:00+09:00", 5.0, listOf(true)))
        api.uploadTrip(request("new", "2026-11-03T18:10:00+09:00", 0.5, listOf(true)))
        api.uploadTrip(request("mid", "2026-11-03T12:00:00+09:00", 5.0, listOf(false)))

        val recent = (api.getRecentTrips(2) as ApiResult.Success).body
        assertEquals(listOf("new", "mid"), recent.map { it.tripId })
        assertEquals(ScoreStatus.EXCLUDED_SHORT, recent[0].status)
        assertEquals(0, recent[1].score)
    }

    @Test
    fun `예전 형식 응답(새 필드 없음)도 읽힘`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = """{"tripId":"a","status":"SCORED","score":75}"""
        val parsed = json.decodeFromString<com.example.blinkerleague.data.remote.dto.TripScoreResponse>(old)
        assertNull(parsed.grade)
        assertEquals(emptyList<PlayDto>(), parsed.plays)
        // 다시 직렬화해도 깨지지 않음
        json.encodeToString(parsed)
    }
}
