package com.example.blinkerleague.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.domain.model.Badge
import com.example.blinkerleague.domain.model.Streak
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AchievementsViewModel(
    private val loadStreak: suspend () -> Streak?,
    private val loadBadges: suspend () -> List<Badge>?,
    private val zone: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AchievementsUiState>(AchievementsUiState.Loading)
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    /** 화면이 보일 때마다(ON_RESUME) 다시 받음 */
    fun refresh() {
        viewModelScope.launch {
            val streak = async { loadStreak() }
            val badges = async { loadBadges() }
            val s = streak.await()
            val b = badges.await()
            _uiState.value = when {
                s == null && b == null ->
                    if (_uiState.value is AchievementsUiState.Loaded) _uiState.value else AchievementsUiState.Unavailable
                else -> AchievementsUiState.Loaded(
                    streak = s?.let { StreakCard(it.days, it.todayAchieved, daysToReward(it.days)) },
                    badges = b.orEmpty().map {
                        BadgeCard(it.name, it.condition, it.earned, it.awardedAtMs?.let { ms -> DATE.format(Instant.ofEpochMilli(ms).atZone(zone)) })
                    },
                )
            }
        }
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")
    }
}
