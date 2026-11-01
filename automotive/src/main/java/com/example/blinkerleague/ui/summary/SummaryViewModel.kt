package com.example.blinkerleague.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.data.repository.TripRepository
import com.example.blinkerleague.domain.model.TripResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SummaryViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: TripRepository,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    val uiState: StateFlow<SummaryUiState> = repository.observeResult(tripId)
        .map { it?.toUiState() ?: SummaryUiState.NotFound }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState.Loading)

    init {
        // 점수를 받으려면 업로드가 필요함. 실패하면 대기열에 남고 다음 기회에 다시 올림 (WorkManager는 3단계에서)
        viewModelScope.launch { repository.uploadPending() }
    }

    private fun TripResult.toUiState() = SummaryUiState.Loaded(
        scoreState = scoreState,
        score = score,
        distanceKm = distanceKm,
        successTurns = turns.count { it.signalOn },
        totalTurns = turns.size,
        turns = turns.map { TurnRow(timeFormat.format(Instant.ofEpochMilli(it.occurredAtMs)), it.direction, it.signalOn) },
    )
}
