package com.example.blinkerleague.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.domain.model.PointWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WalletViewModel(
    private val loadWallet: suspend () -> PointWallet?,
    private val zone: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    /** 화면이 보일 때마다(ON_RESUME) 다시 받음 */
    fun refresh() {
        viewModelScope.launch {
            val wallet = loadWallet()
            _uiState.value = wallet?.let { w ->
                WalletUiState.Loaded(
                    balance = w.balance,
                    todayEarned = w.todayEarned,
                    dailyCap = w.dailyCap,
                    entries = w.entries.map {
                        WalletRow(TIME.format(Instant.ofEpochMilli(it.createdAtMs).atZone(zone)), it.reason, it.amount, it.distanceKm, it.score)
                    },
                )
            } ?: if (_uiState.value is WalletUiState.Loaded) _uiState.value else WalletUiState.Unavailable
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd HH:mm")
    }
}
