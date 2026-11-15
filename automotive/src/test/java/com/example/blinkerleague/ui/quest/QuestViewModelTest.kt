package com.example.blinkerleague.ui.quest

import com.example.blinkerleague.data.celebration.FakeCelebrationStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `다음 클리어가 연속 보상 주기면 보상 포인트, 아니면 비움`() = runTest(dispatcher) {
        // given
        // 기준값 기본(7일마다 50P): 6일 연속이면 오늘 클리어로 7일째, 5일 연속이면 6일째
        val reward = QuestViewModel(loadMission = { mission }, loadStreak = { Streak(6, false) })
        val noReward = QuestViewModel(loadMission = { mission }, loadStreak = { Streak(5, false) })

        // when
        runCurrent()

        // then
        assertEquals(50, loaded(reward).streakImpact?.rewardPoint)
        assertEquals(null, loaded(noReward).streakImpact?.rewardPoint)
    }

    @Test
    fun `클리어한 퀘스트는 처음 볼 때만 축하하고 같은 회차를 다시 열면 축하하지 않는다`() = runTest(dispatcher) {
        // given
        // 같은 저장소를 쓰는 두 번째 ViewModel = 퀘스트 화면을 다시 열거나 앱을 다시 켠 뒤
        val store = FakeCelebrationStore()
        val cleared = mission.copy(status = MissionStatus.ACHIEVED, periodId = "1:2026-11-15")
        val first = QuestViewModel(loadMission = { cleared }, celebrations = store)
        runCurrent()
        val firstCelebrate = loaded(first).celebrate

        // when
        first.onCelebrated()
        val again = QuestViewModel(loadMission = { cleared }, celebrations = store)
        runCurrent()

        // then
        assertTrue(firstCelebrate)
        assertFalse(loaded(again).celebrate)
    }

    @Test
    fun `새 회차에 클리어하면 다시 축하한다`() = runTest(dispatcher) {
        // given
        // 어제 회차는 이미 축하함
        val store = FakeCelebrationStore("1:2026-11-14")
        val today = mission.copy(status = MissionStatus.ACHIEVED, periodId = "1:2026-11-15")

        // when
        val vm = QuestViewModel(loadMission = { today }, celebrations = store)
        runCurrent()

        // then
        assertTrue(loaded(vm).celebrate)
    }
}
