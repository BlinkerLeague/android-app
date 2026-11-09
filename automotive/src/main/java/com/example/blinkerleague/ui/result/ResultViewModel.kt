package com.example.blinkerleague.ui.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TripResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * 경기 결과. 기기 판정 결과(Room)를 바로 보여주고, 업로드·점수 조회가 끝나면 갱신됨 (오프라인이어도 열림).
 *
 * @param sync 업로드 대기열 올리기 + 서버 점수 다시 받기. 실패해도 화면은 기기 결과로 유지
 */
class ResultViewModel(
    result: Flow<TripResult?>,
    private val sync: suspend () -> Unit,
    private val loadMission: suspend () -> TodayMission?,
    private val loadEarnedPoints: suspend () -> Int? = { null },
    private val grades: List<GradeThreshold>,
) : ViewModel() {

    private val mission = MutableStateFlow<TodayMission?>(null)
    private val earnedPoints = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ResultUiState> = combine(result, mission, earnedPoints) { result, mission, points ->
        result?.toResultUiState(mission, grades, points) ?: ResultUiState.NotFound
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultUiState.Loading)

    init {
        viewModelScope.launch {
            sync()
            // 퀘스트 진행률은 이번 운행이 서버에 반영된 뒤에 받아야 맞음
            mission.value = loadMission()
            earnedPoints.value = loadEarnedPoints()
        }
    }

    companion object {
        fun tripId(handle: SavedStateHandle): String = checkNotNull(handle["tripId"])
    }
}

class PlaysViewModel(result: Flow<TripResult?>, zone: ZoneId) : ViewModel() {
    val uiState: StateFlow<PlaysUiState> = result
        .map { it?.toPlaysUiState(zone) ?: PlaysUiState(loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaysUiState())
}
