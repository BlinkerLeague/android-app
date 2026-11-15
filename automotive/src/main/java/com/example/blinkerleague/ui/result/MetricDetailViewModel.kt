package com.example.blinkerleague.ui.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.MetricDetail
import com.example.blinkerleague.domain.model.TripResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/** 기기 기록을 바로 보여주고, 서버 판정 근거를 받으면 갱신 */
class MetricDetailViewModel(
    result: Flow<TripResult?>,
    private val item: MetricItem,
    private val loadServerDetails: suspend () -> List<MetricDetail>?,
    private val grades: List<GradeThreshold>,
    private val tips: Map<String, String>,
    private val zone: ZoneId,
) : ViewModel() {

    private val server = MutableStateFlow<List<MetricDetail>?>(null)

    val uiState: StateFlow<MetricDetailUiState> = combine(result, server) { result, server ->
        result?.toMetricDetail(item, server, grades, tips, zone) ?: MetricDetailUiState.NotFound
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetricDetailUiState.Loading)

    init {
        viewModelScope.launch { server.value = loadServerDetails() }
    }

    companion object {
        fun item(handle: SavedStateHandle): MetricItem =
            MetricItem.entries.firstOrNull { it.name == handle.get<String>("metric") } ?: MetricItem.TURN_SIGNAL
    }
}
