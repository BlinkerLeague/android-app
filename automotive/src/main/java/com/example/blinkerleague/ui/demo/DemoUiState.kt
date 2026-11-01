package com.example.blinkerleague.ui.demo

data class DemoUiState(
    val scenarios: List<ScenarioItem>,
    val speed: Int = 10,
    val phase: DemoPhase = DemoPhase.Ready,
)

data class ScenarioItem(
    val index: Int,
    val name: String,
    val durationSec: Int,
)

sealed interface DemoPhase {
    data object Ready : DemoPhase

    /** 재생 중. 주행 중이므로 점수는 보여주지 않고 진행 상황만 보여줌 */
    data class Running(val scenarioName: String, val progress: Float, val recording: Boolean) : DemoPhase

    /** 운행이 끝남 → 결과 화면으로 이동 */
    data class Finished(val tripId: String) : DemoPhase
}

val SPEED_OPTIONS = listOf(1, 5, 10)
