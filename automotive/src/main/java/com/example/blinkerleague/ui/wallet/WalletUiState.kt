package com.example.blinkerleague.ui.wallet

import com.example.blinkerleague.domain.model.PointReason

/** 지갑 (SCR-06) */
sealed interface WalletUiState {
    data object Loading : WalletUiState

    data object Unavailable : WalletUiState

    data class Loaded(
        val balance: Int,
        val todayEarned: Int,
        val dailyCap: Int?,
        val entries: List<WalletRow>,
    ) : WalletUiState
}

data class WalletRow(
    /** 예: "11.09 08:40" */
    val time: String,
    val reason: PointReason,
    val amount: Int,
    /** 운행 적립일 때 "거리 × 평점" 설명용 */
    val distanceKm: Double?,
    val score: Int?,
)
