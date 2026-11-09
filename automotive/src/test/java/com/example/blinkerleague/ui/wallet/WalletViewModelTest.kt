package com.example.blinkerleague.ui.wallet

import com.example.blinkerleague.domain.model.PointEntry
import com.example.blinkerleague.domain.model.PointReason
import com.example.blinkerleague.domain.model.PointWallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Seoul")
    private val at = ZonedDateTime.of(2026, 11, 9, 8, 40, 0, 0, zone).toInstant().toEpochMilli()
    private var wallet: PointWallet? = PointWallet(32, 32, 150, listOf(PointEntry(12, PointReason.TRIP, 12.3, 100, at)))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `불러오면 잔액·오늘 적립·원장 시각, 실패하면 이전 값 유지 또는 불러오지 못함`() = runTest(dispatcher) {
        val vm = WalletViewModel({ wallet }, zone)
        assertEquals(WalletUiState.Loading, vm.uiState.value)

        vm.refresh()
        runCurrent()
        val loaded = vm.uiState.value as WalletUiState.Loaded
        assertEquals(32, loaded.balance)
        assertEquals("11.09 08:40", loaded.entries.single().time)

        wallet = null // 다시 받다가 실패 → 보던 값 유지
        vm.refresh()
        runCurrent()
        assertEquals(loaded, vm.uiState.value)

        val failing = WalletViewModel({ null }, zone)
        failing.refresh()
        runCurrent()
        assertEquals(WalletUiState.Unavailable, failing.uiState.value)
    }
}
