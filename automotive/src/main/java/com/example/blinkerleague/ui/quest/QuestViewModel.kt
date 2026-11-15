package com.example.blinkerleague.ui.quest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.data.celebration.CelebrationStore
import com.example.blinkerleague.domain.config.PointConfig
import com.example.blinkerleague.domain.model.Streak
import com.example.blinkerleague.domain.model.TodayMission
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QuestViewModel(
    private val loadMission: suspend () -> TodayMission?,
    private val loadStreak: suspend () -> Streak? = { null },
    /** 퀘스트 클리어 축하를 이미 본 회차 */
    private val celebrations: CelebrationStore? = null,
    private val rule: () -> PointConfig = { PointConfig() },
) : ViewModel() {

    private val _uiState = MutableStateFlow<QuestUiState>(QuestUiState.Loading)
    val uiState: StateFlow<QuestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val streak = async { loadStreak() }
            val mission = loadMission()
            _uiState.value = mission?.let { QuestUiState.Loaded(it, streak.await()?.let(::impactOf)) } ?: QuestUiState.Unavailable
        }
    }

    /** 화면이 클리어 축하(꽃가루)를 보여 줬을 때 */
    fun onCelebrated() = Unit

    /**
     * 오늘 클리어 전이면 클리어할 때 이어질 일수(+1), 이미 클리어면 지금 일수.
     * 그 일수가 연속 보상 주기(point.streakRewardDays)의 배수면 보상 포인트
     */
    private fun impactOf(streak: Streak): StreakImpact {
        val days = if (streak.todayAchieved) streak.days else streak.days + 1
        val r = rule()
        return StreakImpact(days, streak.todayAchieved, r.streakRewardPoint.takeIf { days % r.streakRewardDays == 0 })
    }
}
