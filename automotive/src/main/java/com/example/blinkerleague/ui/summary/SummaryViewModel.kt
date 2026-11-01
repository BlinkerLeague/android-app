package com.example.blinkerleague.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.data.repository.MissionRepository
import com.example.blinkerleague.data.repository.TripRepository
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TripResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SummaryViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: TripRepository,
    private val missionRepository: MissionRepository,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    private val mission = MutableStateFlow<TodayMission?>(null)

    val uiState: StateFlow<SummaryUiState> = combine(repository.observeResult(tripId), mission) { result, mission ->
        result?.toUiState(mission) ?: SummaryUiState.NotFound
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState.Loading)

    init {
        viewModelScope.launch {
            // 점수를 받으려면 업로드가 필요함. 실패하면 대기열에 남고 다음 기회에 다시 올림 (WorkManager는 3단계에서)
            repository.uploadPending()
            // 미션 진행률은 이번 운행이 서버에 반영된 뒤에 받아야 맞음
            mission.value = missionRepository.todayMission()
        }
    }

    private fun TripResult.toUiState(mission: TodayMission?) = SummaryUiState.Loaded(
        scoreState = scoreState,
        score = score,
        distanceKm = distanceKm,
        successTurns = turns.count { it.signalOn },
        totalTurns = turns.size,
        turns = turns.map { TurnRow(timeFormat.format(Instant.ofEpochMilli(it.occurredAtMs)), it.direction, it.signalOn) },
        mission = mission,
    )
}
