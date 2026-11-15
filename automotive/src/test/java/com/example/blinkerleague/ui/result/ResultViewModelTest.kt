package com.example.blinkerleague.ui.result

import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.MetricDetail
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
import org.junit.Assert.assertNull
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

    @Test
    fun `짧은 이동은 머리말에 몇 번째 경기인지 담지 않는다`() = runTest(dispatcher) {
        // given
        // 1km 미만이라 경기에서 제외된 오늘 두 번째 운행
        val short = scored.copy(distanceKm = 0.6, scoreState = ScoreState.EXCLUDED_SHORT, score = null, turns = emptyList())
        val vm = ResultViewModel(
            result = flowOf(short),
            sync = {},
            loadMission = { null },
            grades = GradeThreshold.DEFAULT,
            loadDayOrdinal = { 2 },
        )

        // when
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // then
        assertNull((vm.uiState.value as ResultUiState.Loaded).dayOrdinal)
    }

    @Test
    fun `결과 화면 팁은 기준값 signalLeadSec을 쓴다`() = runTest(dispatcher) {
        // given
        // 깜빡이를 켜지 않은 회전 하나 → 실점 1
        val conceded = scored.copy(turns = listOf(TurnRecord(start + 5 * 60_000L, TurnDirection.LEFT, false)))
        val vm = ResultViewModel(
            result = flowOf(conceded),
            sync = {},
            loadMission = { null },
            grades = GradeThreshold.DEFAULT,
            signalLeadSec = 7,
        )

        // when
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // then
        assertEquals(7, (vm.uiState.value as ResultUiState.Loaded).tipLeadSec)
    }

    @Test
    fun `동기화가 끝나면 서버 항목 결과를 불러와 결과 행에 반영한다`() = runTest(dispatcher) {
        // given
        // 서버 항목 결과는 업로드가 끝난 뒤에만 있음. 부드러운 가감속은 SCORED 0점
        var synced = false
        val serverMetrics = listOf(
            MetricDetail(metric = "SMOOTH_DRIVING", status = "SCORED", reason = null, score = 0, counts = emptyMap(), weight = 0.5),
        )
        val vm = ResultViewModel(
            result = flowOf(scored),
            sync = { synced = true },
            loadMission = { null },
            grades = GradeThreshold.DEFAULT,
            loadMetrics = { serverMetrics.takeIf { synced } },
        )

        // when
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // then
        val state = vm.uiState.value as ResultUiState.Loaded
        assertEquals(MetricValue.Score(0), state.metrics.first { it.item == MetricItem.SMOOTH_DRIVING }.value)
    }
}
