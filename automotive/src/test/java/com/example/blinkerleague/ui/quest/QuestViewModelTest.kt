package com.example.blinkerleague.ui.quest

import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.Streak
import com.example.blinkerleague.domain.model.TodayMission
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

@OptIn(ExperimentalCoroutinesApi::class)
class QuestViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val mission = TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 2, 2, 3)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun loaded(vm: QuestViewModel) = vm.uiState.value as QuestUiState.Loaded

    @Test
    fun `오늘 클리어 전이면 클리어 시 이어질 연승 일수`() = runTest(dispatcher) {
        // given
        // 어제까지 5일 연속, 오늘은 아직 클리어 전
        val vm = QuestViewModel(loadMission = { mission }, loadStreak = { Streak(5, false) })

        // when
        runCurrent()

        // then
        assertEquals(StreakImpact(days = 6, todayCleared = false), loaded(vm).streakImpact)
    }

    @Test
    fun `오늘 이미 클리어면 오늘 클리어로 이어진 일수`() = runTest(dispatcher) {
        // given
        // 오늘 클리어해서 6일 연속
        val vm = QuestViewModel(loadMission = { mission }, loadStreak = { Streak(6, true) })

        // when
        runCurrent()

        // then
        assertEquals(StreakImpact(days = 6, todayCleared = true), loaded(vm).streakImpact)
    }
}
