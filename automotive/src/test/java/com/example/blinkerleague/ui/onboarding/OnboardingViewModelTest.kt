package com.example.blinkerleague.ui.onboarding

import com.example.blinkerleague.data.repository.RegisterResult
import com.example.blinkerleague.domain.model.CapabilityReport
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.domain.region.Regions
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
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val speed = MutableStateFlow(PermissionStatus.NOT_REQUESTED)
    private val calls = mutableListOf<Pair<String, String>>()
    private var nextResult = RegisterResult.Registered

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = OnboardingViewModel(
        provinces = Regions.all,
        speedPermission = speed,
        loadCapabilities = { CapabilityReport.ALL_SUPPORTED },
        register = { nickname, region ->
            calls += nickname to region
            nextResult
        },
    )

    @Test
    fun `닉네임 2~12자와 지역을 골라야 입장 가능, 공백은 잘라서 보냄`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(CapabilityReport.ALL_SUPPORTED, vm.uiState.value.capabilities)
        assertFalse(vm.uiState.value.canSubmit)

        vm.onNicknameChange(" 깜빡왕 ")
        assertFalse(vm.uiState.value.canSubmit) // 지역 없음
        vm.onRegionSelect("11440")
        runCurrent()
        assertTrue(vm.uiState.value.canSubmit)

        vm.submit()
        runCurrent()
        assertEquals(listOf("깜빡왕" to "11440"), calls)
        assertTrue(vm.uiState.value.registered)
    }

    @Test
    fun `짧은 닉네임은 보내지 않음, 13자 이상은 잘림`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onRegionSelect("11440")
        vm.onNicknameChange("가")
        vm.submit()
        runCurrent()
        assertEquals(NicknameError.LENGTH, vm.uiState.value.nicknameError)
        assertTrue(calls.isEmpty())

        vm.onNicknameChange("가나다라마바사아자차카타파")
        runCurrent()
        assertEquals(12, vm.uiState.value.nickname.length)
        assertNull(vm.uiState.value.nicknameError)
    }

    @Test
    fun `중복 닉네임·오프라인은 화면에 남고 다시 시도 가능, 속도 권한은 따라감`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onNicknameChange("깜빡왕")
        vm.onRegionSelect("11680")

        nextResult = RegisterResult.NicknameTaken
        vm.submit()
        runCurrent()
        assertEquals(NicknameError.TAKEN, vm.uiState.value.nicknameError)

        nextResult = RegisterResult.Offline
        vm.submit()
        runCurrent()
        assertEquals(SubmitError.OFFLINE, vm.uiState.value.submitError)
        assertFalse(vm.uiState.value.registered)
        assertTrue(vm.uiState.value.canSubmit)

        speed.value = PermissionStatus.DENIED
        runCurrent()
        assertEquals(PermissionStatus.DENIED, vm.uiState.value.speedPermission)
    }
}
