package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.auth.InMemoryTokenStore
import com.example.blinkerleague.data.remote.dto.RegisterDriverResponse
import com.example.blinkerleague.data.remote.http.HttpClientFactory
import com.example.blinkerleague.data.remote.http.HttpLeagueApi
import com.example.blinkerleague.domain.model.Championship
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `GET /api/leagues/current`를 서버 명세(docs/api/openapi.json `CurrentLeagueResponse`) 형식으로 읽는지 확인 */
class LeagueRepositoryTest {

    private val server = MockWebServer().apply { start() }
    private val tokens = InMemoryTokenStore().apply {
        saveRegistration(RegisterDriverResponse(1, "깜빡왕", "11440", "access-1", "refresh-1"))
    }
    private val repository = LeagueRepository(HttpLeagueApi(HttpClientFactory.create(server.url("/").toString(), tokens)))

    @After
    fun tearDown() = server.shutdown()

    private fun respond(body: String) =
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body))

    @Test
    fun `서버 리그 응답을 읽어 내 리그 점수와 순위를 만든다`() = runTest {
        // given
        // 서울 서북권 참여자 12명, 순위 공개, 나는 시즌 60km · 11번 운행으로 2위
        respond(SERVER_LEAGUE)

        // when
        val league = repository.current()

        // then
        assertNotNull(league)
        assertEquals("2026-11", league!!.seasonId)
        val my = league.myLeague!!
        assertEquals("서울 서북권", my.name)
        assertEquals(12, my.participants)
        assertTrue(my.rankPublic)
        assertEquals(2, my.myRank)
        assertEquals(138, my.myScore)
        assertTrue(my.qualified)
        assertEquals(60.5, my.seasonKm, 0.001)
        assertEquals(11, my.seasonTrips)
        assertEquals(listOf(1 to 150, 2 to 138), my.top.map { it.rank to it.score })
        assertEquals(listOf("서울 서북권" to 90, "부산" to null), league.regions.map { it.name to it.safetyIndex })
    }

    @Test
    fun `리그 점수를 평균 점수와 시즌 포인트로 나눠 담는다`() = runTest {
        // given
        // 평균 92.6점 + 시즌 포인트 45P = 리그 점수 137.6
        respond(SERVER_LEAGUE)

        // when
        val my = repository.current()!!.myLeague!!

        // then
        assertEquals(93, my.averageScore)
        assertEquals(45, my.points)
    }

    @Test
    fun `지난 시즌 우승 칭호를 담는다`() = runTest {
        // given
        // 2026-09, 2026-10 시즌에 서울 서북권 우승
        respond(SERVER_LEAGUE)

        // when
        val league = repository.current()!!

        // then
        assertEquals(
            listOf(Championship("2026-09", "서울 서북권"), Championship("2026-10", "서울 서북권")),
            league.championships,
        )
    }

    companion object {
        /** 서버 명세 `CurrentLeagueResponse` 예시 (서버 docs/app-team-changes-2026-11-10.md 형식) */
        val SERVER_LEAGUE = """
            {
              "season": { "seasonId": "2026-11", "startsAt": "2026-11-01T00:00:00+09:00", "endsAt": "2026-12-01T00:00:00+09:00" },
              "league": { "leagueCode": "SEOUL_NORTHWEST", "name": "서울 서북권", "participants": 12, "rankingPublished": true,
                          "safetyIndex": 90.4, "rank": 1,
                          "rankings": [
                            { "rank": 1, "nickname": "안전왕", "averageScore": 100.0, "points": 50, "score": 150.0 },
                            { "rank": 2, "nickname": "깜빡왕", "averageScore": 92.6, "points": 45, "score": 137.6 }
                          ] },
              "me": { "visible": true, "eligible": true, "averageScore": 92.6, "points": 45, "score": 137.6,
                      "rank": 2, "trips": 11, "distanceKm": 60.5,
                      "championships": [
                        { "seasonId": "2026-09", "leagueCode": "SEOUL_NORTHWEST", "name": "서울 서북권" },
                        { "seasonId": "2026-10", "leagueCode": "SEOUL_NORTHWEST", "name": "서울 서북권" }
                      ] },
              "regions": [
                { "leagueCode": "SEOUL_NORTHWEST", "name": "서울 서북권", "safetyIndex": 90.4, "rank": 1 },
                { "leagueCode": "BUSAN", "name": "부산", "safetyIndex": null, "rank": null }
              ]
            }
        """.trimIndent()
    }
}
