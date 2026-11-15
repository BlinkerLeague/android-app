package com.example.blinkerleague.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.data.auth.DriverProfile
import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.config.PointConfig
import com.example.blinkerleague.domain.model.MatchOutcome
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.domain.model.Streak
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.service.RecordingStatus
import com.example.blinkerleague.ui.achievements.StreakCard
import com.example.blinkerleague.ui.achievements.daysToReward
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 홈 (SCR-01, screen-plan 4.3). 운행 결과가 생겨도 자동으로 결과 화면으로 가지 않고, 최근 경기에서 들어감.
 *
 * @param recentTrips 기기 기록의 최근 운행 (최신순)
 * @param refreshRecent 서버 점수로 최근 운행 갱신 (실패해도 조용히 넘어감)
 */
class HomeViewModel(
    recentTrips: Flow<List<TripResult>>,
    recordingStatus: Flow<RecordingStatus>,
    speedPermission: Flow<PermissionStatus>,
    private val loadQuest: suspend () -> TodayMission?,
    private val loadStreak: suspend () -> Streak? = { null },
    private val refreshRecent: suspend () -> Unit,
    private val grades: List<GradeThreshold>,
    private val showDemoEntry: Boolean,
    private val clock: () -> Long,
    private val zone: ZoneId,
    private val rule: () -> PointConfig = { PointConfig() },
    private val profile: () -> DriverProfile? = { null },
) : ViewModel() {

    private val quest = MutableStateFlow<QuestLoad>(QuestLoad.Loading)
    private val streak = MutableStateFlow<Streak?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        combine(quest, streak, ::Pair),
        recentTrips,
        recordingStatus,
        speedPermission,
    ) { (quest, streak), trips, status, speed ->
        HomeUiState(
            streak = streak?.let {
                val r = rule()
                StreakCard(it.days, it.todayAchieved, daysToReward(it.days, r.streakRewardDays), r.streakRewardPoint, r.streakRewardDays)
            },
            quest = (quest as? QuestLoad.Loaded)?.mission,
            questLoading = quest == QuestLoad.Loading,
            recentTrips = trips.map(::toRow),
            recordingStatus = status,
            notices = buildList {
                if (speed != PermissionStatus.GRANTED) add(HomeNotice.NO_SPEED_PERMISSION)
                if (status == RecordingStatus.OFF) add(HomeNotice.RECORDING_OFF)
            },
            showDemoEntry = showDemoEntry,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(showDemoEntry = showDemoEntry))

    init {
        // 홈을 보는 중에 운행이 끝나거나 점수가 들어오면(최근 경기 맨 위가 바뀌면) 퀘스트 진행도도 다시 받음
        viewModelScope.launch {
            recentTrips
                .map { trips -> trips.firstOrNull()?.let { it.tripId to it.scoreState } }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    quest.value = QuestLoad.Loaded(loadQuest())
                    loadStreak()?.let { streak.value = it }
                }
        }
    }

    /** 화면이 보일 때마다(ON_RESUME) 퀘스트·최근 경기 점수를 다시 받음 */
    fun refresh() {
        viewModelScope.launch {
            quest.value = QuestLoad.Loaded(loadQuest())
        }
        viewModelScope.launch { loadStreak()?.let { streak.value = it } }
        viewModelScope.launch { refreshRecent() }
    }

    private fun toRow(result: TripResult): RecentTripRow {
        val at = Instant.ofEpochMilli(result.endedAtMs).atZone(zone)
        val today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate() // LocalDate.ofInstant는 API 34부터
        val day = when (at.toLocalDate()) {
            today -> TripDay.TODAY
            today.minusDays(1) -> TripDay.YESTERDAY
            else -> TripDay.OTHER
        }
        val goals = result.turns.count { it.signalOn }
        val conceded = result.turns.size - goals
        return RecentTripRow(
            tripId = result.tripId,
            day = day,
            date = at.format(DATE),
            time = at.format(TIME),
            scoreState = result.scoreState,
            score = result.score,
            grade = GradeThreshold.gradeOf(result.score, grades),
            goals = goals,
            conceded = conceded,
            outcome = MatchOutcome.of(goals, conceded),
            distanceKm = result.distanceKm,
        )
    }

    private sealed interface QuestLoad {
        data object Loading : QuestLoad
        data class Loaded(val mission: TodayMission?) : QuestLoad
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")
    }
}
