package com.example.blinkerleague.ui.settings

import com.example.blinkerleague.data.auth.DriverProfile
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.domain.region.Regions
import com.example.blinkerleague.service.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val speed = MutableStateFlow(PermissionStatus.DENIED)
    private val status = MutableStateFlow(RecordingStatus.WAITING)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `내 정보는 지역 이름으로, 권한·기록 상태는 따라감`() = runTest(dispatcher) {
        val vm = SettingsViewModel({ DriverProfile("깜빡왕", "11440") }, speed, status, "1.0", usesServer = true)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals("서울 마포구", vm.uiState.value.region)
        assertEquals(PermissionStatus.DENIED, vm.uiState.value.speedPermission)

        speed.value = PermissionStatus.GRANTED
        status.value = RecordingStatus.RECORDING
        runCurrent()
        assertEquals(PermissionStatus.GRANTED, vm.uiState.value.speedPermission)
        assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingStatus)
    }

    @Test
    fun `목록에 없는 지역 코드는 코드 그대로, 등록 전이면 비움`() = runTest(dispatcher) {
        assertEquals("26110", SettingsViewModel({ DriverProfile("a", "26110") }, speed, status, "1.0", false).uiState.value.region)
        assertNull(SettingsViewModel({ null }, speed, status, "1.0", false).uiState.value.nickname)
        assertNull(Regions.label("99999"))
    }
}
