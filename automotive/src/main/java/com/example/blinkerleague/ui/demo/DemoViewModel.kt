package com.example.blinkerleague.ui.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinkerleague.AppContainer
import com.example.blinkerleague.data.location.FakeLocationSource
import com.example.blinkerleague.data.scenario.Scenarios
import com.example.blinkerleague.data.vehicle.FakeVehicleSignalSource
import com.example.blinkerleague.service.RecorderState
import com.example.blinkerleague.service.TripRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 시연·테스트용: 시나리오를 Fake 신호로 재생해 실제 기록 흐름([TripRecorder])을 그대로 돌림.
 * 실차 SDK 구현체가 생기면 공급원만 바꿔 끼우면 됨.
 */
class DemoViewModel(private val container: AppContainer) : ViewModel() {

    private val scenarios = Scenarios.all

    private val _uiState = MutableStateFlow(
        DemoUiState(
            scenarios = scenarios.mapIndexed { i, s -> ScenarioItem(i, s.name, (s.durationMs / 1000).toInt()) },
        ),
    )
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun selectSpeed(speed: Int) = _uiState.update { it.copy(speed = speed) }

    fun play(index: Int) {
        if (job?.isActive == true) return
        val scenario = scenarios[index]
        val speed = _uiState.value.speed
        val realStart = System.currentTimeMillis()
        // 시나리오 시각을 배속에 맞춘 시계. 공급원 타임스탬프와 같은 기준이어야 P단 3분 판단이 맞음
        val clock = { realStart + (System.currentTimeMillis() - realStart) * speed }

        val recorder = TripRecorder(
            vehicle = FakeVehicleSignalSource(scenario, startMs = realStart, speed = speed.toFloat()),
            location = FakeLocationSource(scenario, startMs = realStart, speed = speed.toFloat()),
            repository = container.tripRepository,
            config = container.config,
            clock = clock,
            newId = container::newId,
            tickIntervalMs = 1_000L / speed,
        )

        job = viewModelScope.launch {
            val recording = launch { recorder.run() }
            launch {
                while (true) {
                    val progress = ((clock() - realStart).toFloat() / scenario.durationMs).coerceIn(0f, 1f)
                    val isRecording = recorder.state.value is RecorderState.Recording
                    _uiState.update { it.copy(phase = DemoPhase.Running(scenario.name, progress, isRecording)) }
                    delay(200)
                }
            }
            recorder.state.collect { state ->
                container.voiceGuide.onRecorderState(state)
                if (state is RecorderState.Finished) {
                    recording.cancel()
                    _uiState.update { it.copy(phase = DemoPhase.Finished(state.tripId)) }
                    job?.cancel()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        _uiState.update { it.copy(phase = DemoPhase.Ready) }
    }

    /** 결과 화면으로 이동한 뒤 호출 */
    fun onNavigated() = _uiState.update { it.copy(phase = DemoPhase.Ready) }
}
