package com.example.blinkerleague.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.blinkerleague.data.local.AppDatabase
import com.example.blinkerleague.data.local.UploadState
import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.FakeTripApi
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.Trip
import com.example.blinkerleague.domain.model.TripEvent
import com.example.blinkerleague.domain.model.TurnDirection
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var api: FakeTripApi
    private lateinit var repository: TripRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = FakeTripApi()
        repository = TripRepository(db.tripDao(), api, TripMapper(ZoneId.of("Asia/Seoul"))) { now }
    }

    @After
    fun tearDown() = db.close()

    private fun trip(id: String, startedAtMs: Long, signalOn: List<Boolean>, distanceKm: Double = 5.0) = Trip(
        id = id,
        startedAtMs = startedAtMs,
        endedAtMs = startedAtMs + 600_000,
        distanceKm = distanceKm,
        supported = setOf(SignalType.TURN_SIGNAL),
        events = signalOn.mapIndexed { i, on ->
            TripEvent.TurnEvent("$id-e$i", startedAtMs + (i + 1) * 60_000L, TurnDirection.LEFT, on, uTurn = false)
        },
    )

    private suspend fun stored(id: String) = db.tripDao().trip(id)!!

    @Test
    fun `저장한 운행을 올리면 UPLOADED와 서버 점수가 남음`() = runTest {
        repository.saveFinishedTrip(trip("a", 0, listOf(true, true, true, false)))

        val summary = repository.uploadPending()

        assertEquals(UploadSummary(uploaded = 1, rejected = 0, remaining = 0), summary)
        val a = stored("a").trip
        assertEquals(UploadState.UPLOADED, a.uploadState)
        assertEquals(ScoreStatus.SCORED, a.scoreStatus)
        assertEquals(75, a.score)
        assertEquals(1, a.attemptCount)
        assertEquals(0, repository.observePendingCount().first())
    }

    @Test
    fun `최근 운행은 종료 시각 최신순, 서버 최근 경기로 점수 갱신`() = runTest {
        repository.saveFinishedTrip(trip("old", 0, listOf(true)))
        repository.saveFinishedTrip(trip("new", 1_000_000, listOf(true, false)))
        assertEquals(listOf("new", "old"), repository.observeRecentResults(2).first().map { it.tripId })
        assertEquals(ScoreState.PENDING, repository.observeRecentResults(2).first().first().scoreState)

        // 다른 곳(서버)에 이미 올라가 점수가 난 상태를 흉내: Fake에 직접 올림
        api.uploadTrip(TripMapper(java.time.ZoneId.of("Asia/Seoul")).toUploadRequest(db.tripDao().trip("new")!!))
        repository.refreshRecentScores(2)

        val newest = repository.observeRecentResults(2).first().first()
        assertEquals(ScoreState.SCORED, newest.scoreState)
        assertEquals(50, newest.score)
    }

    @Test
    fun `결과 화면 점수 갱신 - 업로드 전에는 조회하지 않음, 받은 점수는 그대로 둠`() = runTest {
        repository.saveFinishedTrip(trip("a", 0, listOf(true, true)))
        repository.refreshScore("a") // 업로드 전: 아무 일 없음
        assertEquals(ScoreState.PENDING, repository.observeResult("a").first()!!.scoreState)

        repository.uploadPending()
        repository.refreshScore("a")
        assertEquals(100, repository.observeResult("a").first()!!.score)
    }

    @Test
    fun `적립 포인트는 업로드 뒤 점수 응답에서`() = runTest {
        repository.saveFinishedTrip(trip("p", 0, listOf(true)))
        api.earnedPointsLookup = { 7 }
        assertNull(repository.earnedPoints("p")) // 업로드 전
        repository.uploadPending()
        assertEquals(7, repository.earnedPoints("p"))
    }

    @Test
    fun `업로드를 동시에 두 번 불러도 같은 운행은 한 번만 보냄`() = runTest {
        repository.saveFinishedTrip(trip("a", 0, listOf(true)))
        repository.saveFinishedTrip(trip("b", 1_000_000, listOf(false)))

        val first = async { repository.uploadPending() }
        val second = async { repository.uploadPending() }
        val total = first.await().uploaded + second.await().uploaded

        assertEquals(2, total)
        assertEquals(listOf("a", "b"), api.received.map { it.tripId })
    }

    @Test
    fun `같은 운행을 두 번 저장해도 한 건만 남고 이벤트도 중복되지 않음`() = runTest {
        val a = trip("a", 0, listOf(true, false))
        repository.saveFinishedTrip(a)
        repository.saveFinishedTrip(a)

        assertEquals(2, stored("a").events.size)
        assertEquals(1, repository.observePendingCount().first())
    }

    @Test
    fun `네트워크 오류면 PENDING으로 남고 다음 시도에 다시 올림`() = runTest {
        repository.saveFinishedTrip(trip("a", 0, listOf(true)))
        repository.saveFinishedTrip(trip("b", 1_000_000, listOf(false)))

        api.failWith = ApiResult.NetworkError(IOException("offline"))
        assertEquals(UploadSummary(uploaded = 0, rejected = 0, remaining = 2), repository.uploadPending())
        assertEquals(UploadState.PENDING, stored("a").trip.uploadState)
        assertEquals(1, stored("a").trip.attemptCount)
        assertEquals(0, stored("b").trip.attemptCount) // 네트워크 오류 후 나머지는 시도하지 않음

        api.failWith = null
        now = 2_000L
        assertEquals(UploadSummary(uploaded = 2, rejected = 0, remaining = 0), repository.uploadPending())
        assertEquals(listOf("a", "b"), api.received.map { it.tripId }) // 오래된 순
        assertEquals(2_000L, stored("a").trip.lastAttemptAtMs)
    }

    @Test
    fun `5xx는 재전송 대상, 4xx는 REJECTED로 멈춤`() = runTest {
        repository.saveFinishedTrip(trip("a", 0, listOf(true)))

        api.failWith = ApiResult.HttpError(503)
        repository.uploadPending()
        assertEquals(UploadState.PENDING, stored("a").trip.uploadState)

        api.failWith = ApiResult.HttpError(409)
        assertEquals(UploadSummary(uploaded = 0, rejected = 1, remaining = 0), repository.uploadPending())
        assertEquals(UploadState.REJECTED, stored("a").trip.uploadState)
        assertEquals(0, repository.observePendingCount().first())
    }

    @Test
    fun `회전 없는 운행과 1km 미만 운행은 서버 상태를 그대로 저장`() = runTest {
        repository.saveFinishedTrip(trip("no-turn", 0, emptyList()))
        repository.saveFinishedTrip(trip("short", 1_000_000, listOf(true), distanceKm = 0.4))

        repository.uploadPending()

        assertEquals(ScoreStatus.NO_TURN, stored("no-turn").trip.scoreStatus)
        assertNull(stored("no-turn").trip.score)
        assertEquals(ScoreStatus.EXCLUDED_SHORT, stored("short").trip.scoreStatus)
    }

    @Test
    fun `항목 상세는 서버 점수 응답의 적용 가중치를 담는다`() = runTest {
        // given
        // FakeTripApi는 TURN_SIGNAL 항목의 weight를 1.0으로 줌
        repository.saveFinishedTrip(trip("a", 0, listOf(true, false)))
        repository.uploadPending()

        // when
        val details = repository.metricDetails("a")!!

        // then
        assertEquals(1.0, details.single { it.metric == "TURN_SIGNAL" }.weight)
    }

    @Test
    fun `오늘 운행이 그날 몇 번째인지 시작 시각 순으로 센다`() = runTest {
        // given
        val seoul = ZoneId.of("Asia/Seoul")
        fun kst(day: Int, hour: Int, minute: Int) =
            ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, seoul).toInstant().toEpochMilli()
        // 어제 23:50에 시작한 운행은 오늘로 세지 않음
        repository.saveFinishedTrip(trip("yesterday", kst(12, 23, 50), listOf(true)))
        repository.saveFinishedTrip(trip("morning", kst(13, 8, 0), listOf(true)))
        repository.saveFinishedTrip(trip("noon", kst(13, 12, 0), listOf(true)))
        // 지금은 한국 시간 2026-11-13 18:00
        now = kst(13, 18, 0)

        // when
        val ordinal = repository.dayOrdinal("noon", seoul)

        // then
        assertEquals(2, ordinal)
    }
}
