package com.example.blinkerleague.ui.league

import com.example.blinkerleague.domain.config.LeagueConfig
import com.example.blinkerleague.domain.model.Championship
import com.example.blinkerleague.domain.model.LeagueEntry
import com.example.blinkerleague.domain.model.LeagueRegion
import com.example.blinkerleague.domain.model.MyLeague

/** 지역 리그 (SCR-04) */
sealed interface LeagueUiState {
    data object Loading : LeagueUiState

    /** 서버에 리그가 아직 없거나 받지 못함 */
    data object Unavailable : LeagueUiState

    data class Loaded(
        /** 시즌 월 (예: 11) */
        val month: Int,
        /** 시즌 끝까지 남은 날 (D-N) */
        val daysLeft: Int,
        val my: MyLeague?,
        val top: List<LeagueEntry>,
        val regions: List<LeagueRegion>,
        /** 지난 시즌 우승 칭호 */
        val championships: List<Championship> = emptyList(),
        /** 순위 자격 · 공개 기준 (기준값 `league`) */
        val rule: LeagueConfig = LeagueConfig(),
    ) : LeagueUiState
}

/** 순위 자격 기준 (서버 D13 기본값. 기준값 설정에 있으나 앱 안내 문구용) */
internal const val QUALIFY_KM = 50
internal const val QUALIFY_TRIPS = 10
