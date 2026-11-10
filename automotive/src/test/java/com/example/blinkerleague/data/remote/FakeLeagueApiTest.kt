package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.TripEventDto
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import com.example.blinkerleague.data.remote.dto.TurnPayload
import com.example.blinkerleague.data.repository.LeagueRepository
import com.example.blinkerleague.domain.config.LeagueConfig
import com.example.blinkerleague.domain.region.LeagueRegions
import com.example.blinkerleague.ui.league.LeagueUiState
import com.example.blinkerleague.ui.league.LeagueViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class FakeLeagueApiTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Seoul")
    // 2026-11-09 10:00 → 11월 시즌, 12.01 00:00 끝 (D-22)
    private val now = ZonedDateTime.of(2026, 11, 9, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val trips = FakeTripApi()
    private val repository = LeagueRepository(FakeLeagueApi(trips, { "11440" }, { now }, zone))
    private var seq = 0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun trip(month: Int, day: Int, km: Double, vararg on: Boolean) {
        val id = "t${++seq}"
        val end = "2026-%02d-%02dT08:30:00+09:00".format(month, day)
        trips.uploadTrip(
            TripUploadRequest(id, end, end, km, listOf("TURN_SIGNAL"),
                on.mapIndexed { i, s -> TripEventDto("$id-$i", "TURN", end, Json.encodeToJsonElement(TurnPayload("LEFT", s))) }),
        )
    }

    @Test
    fun `달력 월 시즌, 이번 달 운행만 거리 가중 평균, 혼자라 순위 비공개`() = runTest(dispatcher) {
        trip(10, 31, 30.0, false) // 지난달: 빠짐
        trip(11, 2, 10.0, true, true) // 100점
        trip(11, 5, 30.0, true, false) // 50점 → (10×100 + 30×50) / 40 = 62.5 → 63
        val league = repository.current()!!
        assertEquals("2026-11", league.seasonId)
        val my = league.myLeague!!
        assertEquals("서울 서북권", my.name)
        assertEquals(63, my.myScore)
        assertFalse(my.rankPublic)
        assertFalse(my.qualified) // 40km, 2번
        assertEquals(1, my.participants)

        val vm = LeagueViewModel(repository::current) { now }
        vm.refresh()
        runCurrent()
        val state = vm.uiState.value as LeagueUiState.Loaded
        assertEquals(11, state.month)
        assertEquals(22, state.daysLeft)
        assertEquals(emptyList<Any>(), state.top)
    }

    @Test
    fun `서울 생활권 매핑 (서버 D31), 목록 밖은 null`() {
        assertEquals("서울 서북권", LeagueRegions.of("11440"))
        assertEquals("서울 동남권", LeagueRegions.of("11680"))
        assertEquals("서울 도심권", LeagueRegions.of("11110"))
        assertNull(LeagueRegions.of("26110"))
    }

    @Test
    fun `서버에 리그가 없으면(404) 불러오지 못함`() = runTest(dispatcher) {
        val none = LeagueRepository(object : LeagueApi {
            override suspend fun currentLeague() = ApiResult.HttpError(404)
            override suspend fun setVisibility(visible: Boolean) = ApiResult.HttpError(404)
        })
        val vm = LeagueViewModel(none::current) { now }
        vm.refresh()
        runCurrent()
        assertEquals(LeagueUiState.Unavailable, vm.uiState.value)
    }

    @Test
    fun `Fake에서 순위 공개를 끄면 다음 리그 조회에 반영된다`() = runTest(dispatcher) {
        // given
        // 기본은 공개 (서버 D8)
        assertTrue(repository.current()!!.visible)

        // when
        val saved = repository.setVisible(false)

        // then
        assertTrue(saved)
        assertFalse(repository.current()!!.visible)
    }

    @Test
    fun `Fake는 기준값의 순위 자격 거리로 자격을 판정한다`() = runTest(dispatcher) {
        // given
        // 기준값: 시즌 30km 또는 5회. 이번 달 40km · 2번 운행
        val relaxed = LeagueRepository(
            FakeLeagueApi(trips, { "11440" }, { now }, zone) { LeagueConfig(minSeasonDistanceKm = 30.0, minSeasonTrips = 5) },
        )
        trip(11, 2, 10.0, true)
        trip(11, 5, 30.0, true)

        // when
        val my = relaxed.current()!!.myLeague!!

        // then
        assertTrue(my.qualified)
    }
}
