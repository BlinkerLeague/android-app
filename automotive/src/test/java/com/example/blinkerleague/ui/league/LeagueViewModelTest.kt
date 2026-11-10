package com.example.blinkerleague.ui.league

import com.example.blinkerleague.domain.config.LeagueConfig
import com.example.blinkerleague.domain.model.Championship
import com.example.blinkerleague.domain.model.League
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class LeagueViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 11, 10, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val seasonEnd = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `리그 화면 상태에 지난 시즌 우승 칭호를 담는다`() = runTest(dispatcher) {
        // given
        // 2026-10 시즌 서울 서북권 우승
        val titles = listOf(Championship("2026-10", "서울 서북권"))
        val vm = LeagueViewModel({ League("2026-11", seasonEnd, null, emptyList(), titles) }) { now }

        // when
        vm.refresh()
        runCurrent()

        // then
        assertEquals(titles, (vm.uiState.value as LeagueUiState.Loaded).championships)
    }

    @Test
    fun `순위 자격과 공개 인원 안내에 기준값을 쓴다`() = runTest(dispatcher) {
        // given
        // 서버 기준값: 30km 또는 5회, 20명부터 순위 공개
        val rule = LeagueConfig(minParticipants = 20, minSeasonDistanceKm = 30.0, minSeasonTrips = 5)
        val vm = LeagueViewModel({ League("2026-11", seasonEnd, null, emptyList()) }, { rule }) { now }

        // when
        vm.refresh()
        runCurrent()

        // then
        assertEquals(rule, (vm.uiState.value as LeagueUiState.Loaded).rule)
    }
}
