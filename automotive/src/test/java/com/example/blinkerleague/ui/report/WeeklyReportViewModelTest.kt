package com.example.blinkerleague.ui.report

import com.example.blinkerleague.domain.model.MetricChange
import com.example.blinkerleague.domain.model.WeeklyMission
import com.example.blinkerleague.domain.model.WeeklyMissionStatus
import com.example.blinkerleague.domain.model.WeeklyReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Seoul")
    // 2026-11-09(월) → 볼 수 있는 가장 최근 주는 W45(11.02~11.08)
    private val now = ZonedDateTime.of(2026, 11, 9, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val requested = mutableListOf<String?>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun report(week: String) = WeeklyReport(
        week, 5, 42.3,
        listOf(MetricChange("TURN_SIGNAL", 75, 70, 5), MetricChange("SMOOTH_DRIVING", 88, null, null)),
        "TURN_SIGNAL", null,
        WeeklyMission("2026-W46", "TURN_SIGNAL", "회전 깜빡이 주간 90점", 90, 30.0, WeeklyMissionStatus.ASSIGNED),
    )

    private fun viewModel() = WeeklyReportViewModel({ week -> requested += week; report(week ?: "2026-W45") }, { now }, zone)

    @Test
    fun `처음은 서버 기본(지난주), 날짜 범위와 가장 약한 항목`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        val state = vm.uiState.value as WeeklyReportUiState.Loaded
        assertEquals(listOf<String?>(null), requested)
        assertEquals("11.02 ~ 11.08", state.range)
        assertFalse(state.canGoNext)
        assertTrue(state.metrics.first().weakest)
        assertEquals("회전 깜빡이 주간 90점", state.nextMission!!.title)
    }

    @Test
    fun `이전 주로 갔다가 다음 주로 돌아옴, 지난주보다 뒤로는 못 감`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.previousWeek()
        runCurrent()
        assertEquals("2026-W44", (vm.uiState.value as WeeklyReportUiState.Loaded).weekKey)
        assertTrue((vm.uiState.value as WeeklyReportUiState.Loaded).canGoNext)

        vm.nextWeek()
        runCurrent()
        vm.nextWeek() // 이미 지난주 → 요청 안 함
        runCurrent()
        assertEquals(listOf(null, "2026-W44", "2026-W45"), requested)
    }

    @Test
    fun `ISO 주 키 - 연말 경계 포함`() {
        assertEquals("2026-W45", IsoWeek.key(LocalDate.of(2026, 11, 2)))
        assertEquals(LocalDate.of(2026, 11, 2), IsoWeek.monday("2026-W45"))
        assertEquals("2027-W01", IsoWeek.shift("2026-W53", 1))
        assertEquals(LocalDate.of(2026, 11, 2), IsoWeek.mondayOf(LocalDate.of(2026, 11, 8)))
    }
}
