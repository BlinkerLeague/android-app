package com.example.blinkerleague.ui.quest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.data.celebration.CelebrationStore
import com.example.blinkerleague.domain.config.PointConfig
import com.example.blinkerleague.domain.model.MissionStatus
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
            _uiState.value = mission?.let {
                QuestUiState.Loaded(it, streak.await()?.let(::impactOf), celebrate = shouldCelebrate(it))
            } ?: QuestUiState.Unavailable
        }
    }

    /** 화면이 클리어 축하(꽃가루)를 보여 줬을 때 */
    fun onCelebrated() {
        val mission = (_uiState.value as? QuestUiState.Loaded)?.mission ?: return
        // 지금 화면의 꽃가루는 그대로 두고, 다음에 열 때부터 끔
        mission.periodId?.let { celebrations?.markCelebrated(it) }
    }

    /** 클리어했고 이 회차를 아직 축하하지 않았으면. 회차 키를 모르면 기억할 수 없으므로 화면 진입마다 축하 */
    private fun shouldCelebrate(mission: TodayMission): Boolean {
        if (mission.status != MissionStatus.ACHIEVED) return false
        val key = mission.periodId ?: return true
        return celebrations?.isCelebrated(key) != true
    }

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
