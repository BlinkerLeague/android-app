package com.example.blinkerleague.domain.model

enum class Availability {
    SUPPORTED,

    /** 이 차량(SDK)이 지원하지 않음. 측정에서 빼고 화면에는 "이 차량 미지원" */
    UNSUPPORTED,

    /** 런타임 권한을 허용받지 못함 (예: `CAR_SPEED`) */
    NO_PERMISSION,
}

/**
 * 이 차량에서 측정에 쓸 수 있는 신호 (SDK checkXXXCapability·권한 확인 결과).
 * 미지원이면 크래시나 0점이 아니라 측정에서 제외함 (CLAUDE.md).
 */
data class CapabilityReport(
    val turnSignal: Availability,
    val steering: Availability,
    val gear: Availability,
    val speed: Availability,
    /** 브레이크 페달 (급감속 판정) */
    val brake: Availability = Availability.UNSUPPORTED,
    /** 하향등 (야간 전조등 판정) */
    val headlight: Availability = Availability.UNSUPPORTED,
) {
    /** 업로드 `supported` 목록에 넣을 신호 */
    val supportedSignals: Set<SignalType>
        get() = buildSet {
            if (turnSignal == Availability.SUPPORTED) add(SignalType.TURN_SIGNAL)
            if (steering == Availability.SUPPORTED) add(SignalType.STEERING_ANGLE)
            if (speed == Availability.SUPPORTED) add(SignalType.SPEED)
            if (brake == Availability.SUPPORTED) add(SignalType.BRAKE)
        }

    /** 회전 깜빡이 판정에는 방향지시등과 핸들 각도가 둘 다 필요함 */
    val canMeasureTurns: Boolean
        get() = turnSignal == Availability.SUPPORTED && steering == Availability.SUPPORTED

    companion object {
        val ALL_SUPPORTED = CapabilityReport(
            Availability.SUPPORTED, Availability.SUPPORTED, Availability.SUPPORTED, Availability.SUPPORTED, Availability.SUPPORTED,
            Availability.SUPPORTED,
        )
    }
}
