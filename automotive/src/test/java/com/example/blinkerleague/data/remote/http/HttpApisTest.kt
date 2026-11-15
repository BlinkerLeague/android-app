package com.example.blinkerleague.data.remote.http

import com.example.blinkerleague.data.auth.InMemoryTokenStore
import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.dto.RegisterDriverRequest
import com.example.blinkerleague.data.remote.dto.RegisterDriverResponse
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.data.remote.dto.TripEventDto
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import com.example.blinkerleague.data.repository.DriverRepository
import com.example.blinkerleague.data.repository.RegisterResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 서버 형식(서버 저장소 Controller 기준)으로 주고받는지 MockWebServer로 확인 */
class HttpApisTest {

    private val server = MockWebServer().apply { start() }
    private val tokens = InMemoryTokenStore().apply {
        saveRegistration(RegisterDriverResponse(1, "깜빡왕", "11680", "access-1", "refresh-1"))
    }
    private val service = HttpClientFactory.create(server.url("/").toString(), tokens)
    private val trips = HttpTripApi(service)

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val upload = TripUploadRequest(
        tripId = "t1",
        startedAt = "2026-11-03T08:00:00+09:00",
        endedAt = "2026-11-03T08:30:00+09:00",
        distanceKm = 5.0,
        supported = listOf("TURN_SIGNAL"),
        events = listOf(
            TripEventDto("e1", "TURN", "2026-11-03T08:05:00+09:00", Json.parseToJsonElement("""{"direction":"LEFT","signalOn":true}""")),
        ),
    )

    @Test
    fun `업로드 - Bearer 헤더와 서버 형식 본문, 지금 서버 응답(tripId·status만)도 읽힘`() = runTest {
        server.enqueue(json(201, """{"tripId":"t1","status":"UPLOADED"}"""))

        val result = trips.uploadTrip(upload) as ApiResult.Success
        assertEquals(ScoreStatus.UPLOADED, result.body.status)
        assertNull(result.body.score)

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/trips", sent.path)
        assertEquals("Bearer access-1", sent.getHeader("Authorization"))
        val body = Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals("t1", body["tripId"]!!.jsonPrimitive.content)
        assertEquals("TURN_SIGNAL", body["supported"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("LEFT", body["events"]!!.jsonArray[0].jsonObject["payload"]!!.jsonObject["direction"]!!.jsonPrimitive.content)
    }

    @Test
    fun `401이면 토큰을 재발급받아 한 번 다시 보냄`() = runTest {
        server.enqueue(json(401, """{"code":"INVALID_TOKEN","message":"만료"}"""))
        server.enqueue(json(200, """{"accessToken":"access-2","refreshToken":"refresh-2"}"""))
        server.enqueue(json(200, """{"tripId":"t1","status":"UPLOADED"}"""))

        assertTrue(trips.uploadTrip(upload) is ApiResult.Success)

        server.takeRequest() // 첫 시도
        val refresh = server.takeRequest()
        assertEquals("/api/auth/refresh", refresh.path)
        assertEquals("""{"refreshToken":"refresh-1"}""", refresh.body.readUtf8())
        assertEquals("Bearer access-2", server.takeRequest().getHeader("Authorization"))
        assertEquals("refresh-2", tokens.refreshToken)
    }

    @Test
    fun `재발급도 거부되면 토큰을 지우고 401 - 재등록 필요`() = runTest {
        server.enqueue(json(401, """{"code":"INVALID_TOKEN","message":"만료"}"""))
        server.enqueue(json(401, """{"code":"INVALID_TOKEN","message":"재사용"}"""))

        assertEquals(ApiResult.HttpError(401), trips.uploadTrip(upload))
        assertFalse(tokens.registered.value)
        assertNull(tokens.accessToken)
    }

    @Test
    fun `점수 404, 최근 경기 목록, 서버 꺼짐`() = runTest {
        server.enqueue(json(404, """{"code":"NOT_FOUND","message":"없음"}"""))
        assertEquals(ApiResult.HttpError(404), trips.getScore("x"))

        server.enqueue(json(200, """[{"tripId":"a","endedAt":"2026-11-03T18:10:00+09:00","distanceKm":12.4,"status":"SCORED","score":67,"grade":null}]"""))
        val recent = trips.getRecentTrips(2) as ApiResult.Success
        assertEquals("/api/trips/x/score", server.takeRequest().path)
        assertEquals("/api/trips?limit=2", server.takeRequest().path)
        assertEquals(67, recent.body.single().score)

        server.shutdown()
        assertTrue(trips.getScore("x") is ApiResult.NetworkError)
    }

    @Test
    fun `응답 형식이 다르면 재전송하지 않는 오류로`() = runTest {
        server.enqueue(json(200, """{"unexpected":true}"""))
        assertEquals(ApiResult.HttpError(UNREADABLE_RESPONSE), trips.uploadTrip(upload))
    }

    @Test
    fun `등록 - 성공하면 토큰 저장, 닉네임 중복은 409`() = runTest {
        val fresh = InMemoryTokenStore()
        val repository = DriverRepository(HttpDriverApi(HttpClientFactory.create(server.url("/").toString(), fresh)), fresh)
        server.enqueue(json(409, """{"code":"CONFLICT","message":"중복"}"""))
        server.enqueue(json(201, """{"driverId":7,"nickname":"깜빡왕","regionCode":"11680","accessToken":"a","refreshToken":"r"}"""))

        assertEquals(RegisterResult.NicknameTaken, repository.register("깜빡왕", "11680"))
        assertEquals(RegisterResult.Registered, repository.register("깜빡왕", "11680"))
        assertTrue(fresh.registered.value)
        assertEquals("a", fresh.accessToken)

        val first = server.takeRequest()
        assertEquals("/api/drivers", first.path)
        assertNull(first.getHeader("Authorization"))
        assertEquals("""{"nickname":"깜빡왕","regionCode":"11680"}""", first.body.readUtf8())
    }

    @Test
    fun `등록 요청 DTO 직렬화`() {
        assertEquals(
            """{"nickname":"a","regionCode":"11680"}""",
            HttpClientFactory.json.encodeToString(RegisterDriverRequest.serializer(), RegisterDriverRequest("a", "11680")),
        )
    }
}
