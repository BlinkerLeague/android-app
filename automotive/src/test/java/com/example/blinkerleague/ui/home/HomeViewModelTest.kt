package com.example.blinkerleague.ui.home

import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.service.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 11, 8, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val trips = MutableStateFlow<List<TripResult>>(emptyList())
    private val status = MutableStateFlow(RecordingStatus.WAITING)
    private val speed = MutableStateFlow(PermissionStatus.GRANTED)
    private var refreshCount = 0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun trip(id: String, endedAt: ZonedDateTime, state: ScoreState, score: Int?) =
        TripResult(id, 0, endedAt.toInstant().toEpochMilli(), 5.0, state, score, emptyList())

    private fun viewModel(quest: TodayMission?) = HomeViewModel(
        recentTrips = trips,
        recordingStatus = status,
        speedPermission = speed,
        loadQuest = { quest },
        refreshRecent = { refreshCount++ },
        grades = GradeThreshold.DEFAULT,
        showDemoEntry = false,
        clock = { now },
        zone = zone,
    )

    @Test
    fun `최근 경기 - 오늘·어제·날짜, 등급은 점수에서, 50점 미만은 등급 없음`() = runTest(dispatcher) {
        val vm = viewModel(null)
        backgroundScope.launch { vm.uiState.collect {} }
        trips.value = listOf(
            trip("a", ZonedDateTime.of(2026, 11, 8, 8, 42, 0, 0, zone), ScoreState.SCORED, 100),
            trip("b", ZonedDateTime.of(2026, 11, 7, 18, 10, 0, 0, zone), ScoreState.SCORED, 33),
            trip("c", ZonedDateTime.of(2026, 11, 6, 8, 0, 0, 0, zone), ScoreState.PENDING, null),
        )
        runCurrent()

        val rows = vm.uiState.value.recentTrips
        assertEquals(listOf(TripDay.TODAY, TripDay.YESTERDAY, TripDay.OTHER), rows.map { it.day })
        assertEquals("08:42", rows[0].time)
        assertEquals("11.06", rows[2].date)
        assertEquals("S", rows[0].grade)
        assertNull(rows[1].grade)
        assertEquals(ScoreState.PENDING, rows[2].scoreState)
    }

    @Test
    fun `퀘스트는 불러오기 전 로딩, refresh 하면 퀘스트와 서버 점수를 다시 받음`() = runTest(dispatcher) {
        val quest = TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 2, 2, 3)
        val vm = viewModel(quest)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.questLoading)

        vm.refresh()
        runCurrent()
        assertEquals(quest, vm.uiState.value.quest)
        assertEquals(false, vm.uiState.value.questLoading)
        assertEquals(1, refreshCount)
    }

    @Test
    fun `홈을 보는 중 새 운행이 들어오거나 점수가 바뀌면 퀘스트를 다시 받음`() = runTest(dispatcher) {
        var loads = 0
        val vm = HomeViewModel(
            recentTrips = trips, recordingStatus = status, speedPermission = speed,
            loadQuest = { loads++; null }, refreshRecent = {}, grades = GradeThreshold.DEFAULT,
            showDemoEntry = false, clock = { now }, zone = zone,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(0, loads) // 처음 목록은 refresh(ON_RESUME)가 맡음

        val ended = ZonedDateTime.of(2026, 11, 8, 8, 42, 0, 0, zone)
        trips.value = listOf(trip("new", ended, ScoreState.PENDING, null))
        runCurrent()
        assertEquals(1, loads)

        trips.value = listOf(trip("new", ended, ScoreState.SCORED, 67))
        runCurrent()
        assertEquals(2, loads)
    }

    @Test
    fun `연속 달성은 refresh 때 받고, 못 받으면 카드 숨김`() = runTest(dispatcher) {
        val vm = HomeViewModel(
            recentTrips = trips, recordingStatus = status, speedPermission = speed,
            loadQuest = { null }, loadStreak = { com.example.blinkerleague.domain.model.Streak(5, true) },
            refreshRecent = {}, grades = GradeThreshold.DEFAULT, showDemoEntry = false, clock = { now }, zone = zone,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertNull(vm.uiState.value.streakDays)

        vm.refresh()
        runCurrent()
        assertEquals(5, vm.uiState.value.streakDays)
        assertTrue(vm.uiState.value.streakTodayAchieved)
    }

    @Test
    fun `속도 권한이 없거나 기록이 꺼지면 안내`() = runTest(dispatcher) {
        val vm = viewModel(null)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(emptyList<HomeNotice>(), vm.uiState.value.notices)

        speed.value = PermissionStatus.DENIED
        status.value = RecordingStatus.OFF
        runCurrent()
        assertEquals(listOf(HomeNotice.NO_SPEED_PERMISSION, HomeNotice.RECORDING_OFF), vm.uiState.value.notices)
    }

    @Test
    fun `등급 기준 경계`() {
        assertEquals("S", GradeThreshold.gradeOf(95, GradeThreshold.DEFAULT))
        assertEquals("A", GradeThreshold.gradeOf(94, GradeThreshold.DEFAULT))
        assertEquals("C", GradeThreshold.gradeOf(50, GradeThreshold.DEFAULT))
        assertNull(GradeThreshold.gradeOf(49, GradeThreshold.DEFAULT))
        assertNull(GradeThreshold.gradeOf(null, GradeThreshold.DEFAULT))
    }
}
