package com.example.blinkerleague.ui.settings

import com.example.blinkerleague.data.auth.DriverProfile
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.domain.region.Regions
import com.example.blinkerleague.service.RecordingStatus
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `설정을 열면 서버의 순위 공개 상태를 보여준다`() = runTest(dispatcher) {
        // given
        // 서버에는 순위 공개가 꺼져 있음
        val vm = SettingsViewModel({ DriverProfile("깜빡왕", "11440") }, speed, status, "1.0", true, loadLeagueVisible = { false })

        // when
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        // then
        assertEquals(false, vm.uiState.value.leagueVisible)
    }

    @Test
    fun `순위 공개를 끄면 서버에 저장하고 토글이 꺼진다`() = runTest(dispatcher) {
        // given
        val saved = mutableListOf<Boolean>()
        val vm = SettingsViewModel(
            { DriverProfile("깜빡왕", "11440") }, speed, status, "1.0", true,
            loadLeagueVisible = { true },
            saveLeagueVisible = { saved += it; true },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        // when
        vm.setLeagueVisible(false)
        runCurrent()

        // then
        assertEquals(listOf(false), saved)
        assertEquals(false, vm.uiState.value.leagueVisible)
        assertFalse(vm.uiState.value.leagueVisibilityFailed)
    }

    @Test
    fun `서버 저장에 실패하면 토글은 그대로 두고 실패를 알린다`() = runTest(dispatcher) {
        // given
        // 오프라인이라 저장 실패
        val vm = SettingsViewModel(
            { DriverProfile("깜빡왕", "11440") }, speed, status, "1.0", true,
            loadLeagueVisible = { true },
            saveLeagueVisible = { false },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        // when
        vm.setLeagueVisible(false)
        runCurrent()

        // then
        assertEquals(true, vm.uiState.value.leagueVisible)
        assertTrue(vm.uiState.value.leagueVisibilityFailed)
    }

    @Test
    fun `저장 중에 토글을 다시 누르면 서버에 한 번만 저장을 요청한다`() = runTest(dispatcher) {
        // given
        // 느린 네트워크라 첫 저장이 끝나지 않음
        val gate = CompletableDeferred<Boolean>()
        val saved = mutableListOf<Boolean>()
        val vm = SettingsViewModel(
            { DriverProfile("깜빡왕", "11440") }, speed, status, "1.0", true,
            loadLeagueVisible = { true },
            saveLeagueVisible = { saved += it; gate.await() },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        // when
        vm.setLeagueVisible(false)
        runCurrent()
        vm.setLeagueVisible(false)
        runCurrent()

        // then
        assertEquals(listOf(false), saved)
    }
}
