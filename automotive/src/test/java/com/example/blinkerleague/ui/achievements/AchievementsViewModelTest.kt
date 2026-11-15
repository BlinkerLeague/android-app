package com.example.blinkerleague.ui.achievements

import com.example.blinkerleague.domain.config.PointConfig
import com.example.blinkerleague.domain.model.Badge
import com.example.blinkerleague.domain.model.Streak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class AchievementsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Seoul")
    private val at = ZonedDateTime.of(2026, 11, 9, 21, 13, 0, 0, zone).toInstant().toEpochMilli()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `연속 달성과 배지, 다음 보상까지 남은 날`() = runTest(dispatcher) {
        val vm = AchievementsViewModel(
            { Streak(5, false) },
            { listOf(Badge("FIRST_TRIP", "첫 경기", "점수가 난 첫 운행", true, at), Badge("STREAK_7", "7일 연속", "…", false, null)) },
            zone,
        )
        vm.refresh()
        runCurrent()
        val state = vm.uiState.value as AchievementsUiState.Loaded
        assertEquals(StreakCard(5, false, 2), state.streak)
        assertEquals("11.09", state.badges[0].awardedOn)
        assertNull(state.badges[1].awardedOn)
    }

    @Test
    fun `하나만 받아도 보여주고, 둘 다 못 받으면 불러오지 못함`() = runTest(dispatcher) {
        val onlyBadges = AchievementsViewModel({ null }, { emptyList() }, zone)
        onlyBadges.refresh()
        runCurrent()
        assertNull((onlyBadges.uiState.value as AchievementsUiState.Loaded).streak)

        val none = AchievementsViewModel({ null }, { null }, zone)
        none.refresh()
        runCurrent()
        assertEquals(AchievementsUiState.Unavailable, none.uiState.value)
    }

    @Test
    fun `연속 보상 주기와 보상 포인트를 기준값에서 읽는다`() = runTest(dispatcher) {
        // given
        // 서버 기준값: 5일마다 30P, 지금 3일 연속
        val vm = AchievementsViewModel(
            { Streak(3, false) },
            { emptyList() },
            zone,
            rule = { PointConfig(streakRewardDays = 5, streakRewardPoint = 30) },
        )

        // when
        vm.refresh()
        runCurrent()

        // then
        val state = vm.uiState.value as AchievementsUiState.Loaded
        assertEquals(StreakCard(3, false, daysToReward = 2, rewardPoint = 30, period = 5), state.streak)
    }

    @Test
    fun `업적 연승 카드에 기준값 주기와 채운 칸 수`() = runTest(dispatcher) {
        // given
        // 서버 기준값: 5일마다 보상, 지금 3일 연속
        val vm = AchievementsViewModel(
            { Streak(3, true) },
            { emptyList() },
            zone,
            rule = { PointConfig(streakRewardDays = 5, streakRewardPoint = 30) },
        )

        // when
        vm.refresh()
        runCurrent()

        // then
        val streak = (vm.uiState.value as AchievementsUiState.Loaded).streak!!
        assertEquals(5, streak.period)
        assertEquals(3, streakDotsFilled(streak.days, streak.period))
    }

    @Test
    fun `7일마다 보상 - 남은 날 계산`() {
        assertEquals(7, daysToReward(0))
        assertEquals(4, daysToReward(3))
        assertEquals(7, daysToReward(7))
        assertEquals(1, daysToReward(13))
    }

    @Test
    fun `연승 진행 점 - 연승 3일이면 주기 7칸 중 3칸`() {
        // when
        val filled = streakDotsFilled(days = 3, period = 7)

        // then
        assertEquals(3, filled)
    }

    @Test
    fun `연승이 주기의 배수면 진행 점을 모두 채운다`() {
        // when
        val filled = listOf(7, 14).map { streakDotsFilled(days = it, period = 7) }

        // then
        assertEquals(listOf(7, 7), filled)
    }

    @Test
    fun `연승 0일이면 진행 점을 채우지 않는다`() {
        // when
        val filled = streakDotsFilled(days = 0, period = 7)

        // then
        assertEquals(0, filled)
    }
}
