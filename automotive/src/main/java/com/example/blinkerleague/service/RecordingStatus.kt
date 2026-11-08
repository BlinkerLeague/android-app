package com.example.blinkerleague.service

/**
 * 운행 수집 서비스 상태. 화면(홈의 기록 상태 줄, DRV 화면)이 "기록이 꺼져 있어요"를 표시할 때 씀 (docs/screen-plan.md A9).
 */
enum class RecordingStatus {
    /** 서비스가 돌고 있지 않음 → 신호를 받지 못함 */
    OFF,

    /** 서비스는 돌고 있고 운행 시작(기어 D·R·N)을 기다리는 중 */
    WAITING,

    /** 운행 기록 중 */
    RECORDING,
}

internal fun RecorderState.toStatus(): RecordingStatus = when (this) {
    is RecorderState.Recording -> RecordingStatus.RECORDING
    RecorderState.Idle, is RecorderState.Finished -> RecordingStatus.WAITING
}
