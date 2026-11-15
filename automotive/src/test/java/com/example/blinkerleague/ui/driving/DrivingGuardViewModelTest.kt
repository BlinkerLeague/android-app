package com.example.blinkerleague.ui.driving

import com.example.blinkerleague.domain.config.GuardConfig
import com.example.blinkerleague.domain.detection.GuardState
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.service.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrivingGuardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val gears = MutableSharedFlow<Gear>()
    private val speeds = MutableSharedFlow<Float>()
    private val status = MutableStateFlow(RecordingStatus.WAITING)
    private val bypass = MutableStateFlow(false)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = DrivingGuardViewModel(gears, speeds, status, bypass, GuardConfig(gearTimeoutSec = 3))

    @Test
    fun `기어를 받기 전에는 판정 전, 3초 넘게 못 받으면 차량 상태 불명`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertNull(vm.uiState.value.guard)
        assertFalse(vm.uiState.value.showParkedScreens)

        advanceTimeBy(3_001)
        assertEquals(GuardState.VEHICLE_UNKNOWN, vm.uiState.value.guard)
    }

    @Test
    fun `P에서 D로 바꾸면 바로 주행, 다시 P면 정차 화면`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        gears.emit(Gear.P)
        runCurrent()
        assertTrue(vm.uiState.value.showParkedScreens)

        gears.emit(Gear.D)
        runCurrent()
        assertEquals(GuardState.DRIVING, vm.uiState.value.guard)

        gears.emit(Gear.P)
        speeds.emit(20f) // 아직 굴러가는 중
        runCurrent()
        assertEquals(GuardState.DRIVING, vm.uiState.value.guard)

        speeds.emit(0f)
        runCurrent()
        assertTrue(vm.uiState.value.showParkedScreens)
    }

    @Test
    fun `디버그 우회를 켜면 기어와 상관없이 정차 화면`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        gears.emit(Gear.D)
        bypass.value = true
        runCurrent()
        assertTrue(vm.uiState.value.showParkedScreens)
        assertEquals(RecordingStatus.WAITING, vm.uiState.value.recordingStatus)
    }
}
