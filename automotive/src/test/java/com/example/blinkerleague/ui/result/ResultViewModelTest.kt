package com.example.blinkerleague.ui.result

import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Seoul")
    private val start = ZonedDateTime.of(2026, 11, 13, 8, 10, 0, 0, zone).toInstant().toEpochMilli()

    private val scored = TripResult(
        tripId = "t",
        startedAtMs = start,
        endedAtMs = start + 32 * 60_000,
        distanceKm = 14.2,
        scoreState = ScoreState.SCORED,
        score = 67,
        turns = listOf(TurnRecord(start + 5 * 60_000L, TurnDirection.LEFT, true)),
        supported = setOf(SignalType.TURN_SIGNAL, SignalType.STEERING_ANGLE),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `머리말에 오늘 몇 번째 경기인지 담는다`() = runTest(dispatcher) {
        // given
        // 오늘 두 번째 운행
        val vm = ResultViewModel(
            result = flowOf(scored),
            sync = {},
            loadMission = { null },
            grades = GradeThreshold.DEFAULT,
            loadDayOrdinal = { 2 },
        )

        // when
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // then
        assertEquals(2, (vm.uiState.value as ResultUiState.Loaded).dayOrdinal)
    }
}
