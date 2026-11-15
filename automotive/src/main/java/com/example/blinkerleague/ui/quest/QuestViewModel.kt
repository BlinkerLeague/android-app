package com.example.blinkerleague.ui.quest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    /** 오늘 클리어 전이면 클리어할 때 이어질 일수(내일 0이 되기 전 +1), 이미 클리어면 지금 일수 */
    private fun impactOf(streak: Streak): StreakImpact =
        StreakImpact(if (streak.todayAchieved) streak.days else streak.days + 1, streak.todayAchieved)
}
