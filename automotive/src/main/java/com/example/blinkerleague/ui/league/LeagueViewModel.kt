package com.example.blinkerleague.ui.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.domain.config.LeagueConfig
import com.example.blinkerleague.domain.model.League
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil

class LeagueViewModel(
    private val loadLeague: suspend () -> League?,
    /** 순위 자격 · 공개 기준 (기준값 `league`) */
    private val rule: () -> LeagueConfig = { LeagueConfig() },
    private val clock: () -> Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LeagueUiState>(LeagueUiState.Loading)
    val uiState: StateFlow<LeagueUiState> = _uiState.asStateFlow()

    /** 화면이 보일 때마다(ON_RESUME) 다시 받음 */
    fun refresh() {
        viewModelScope.launch {
            val league = loadLeague()
            _uiState.value = league?.let { l ->
                LeagueUiState.Loaded(
                    month = l.seasonId.substringAfter("-").toIntOrNull() ?: 0,
                    daysLeft = ceil((l.seasonEndsAtMs - clock()).coerceAtLeast(0) / DAY_MS).toInt(),
                    my = l.myLeague,
                    top = l.myLeague?.takeIf { it.rankPublic }?.top.orEmpty(),
                    regions = l.regions,
                    championships = l.championships,
                )
            } ?: if (_uiState.value is LeagueUiState.Loaded) _uiState.value else LeagueUiState.Unavailable
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000.0
    }
}
