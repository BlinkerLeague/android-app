package com.example.blinkerleague.domain.model

/**
 * 서버 점수 항목 (서버 `threshold/domain/ScoreItem`). 업로드 요청의 `supported`는 이 이름 목록임.
 * 앱의 신호([SignalType])가 아니라 "이 운행에서 측정할 수 있었던 점수 항목"을 보냄.
 */
enum class ScoreItem {
    TURN_SIGNAL,
    SMOOTH_DRIVING,
    SAFETY_WARNING,
    NIGHT_HEADLIGHT,

    /** 깜빡이 켜 둔 채 잊음 (서버 기준값 `signalCancel`) */
    SIGNAL_CANCEL,

    /** 회전 속도 (서버 기준값 `turnSpeedKmh`) */
    TURN_SPEED,
    ;

    companion object {
        /**
         * 지원 신호 → 측정 가능한 점수 항목.
         * - 깜빡이 항목은 방향지시등과 핸들 각도(회전 감지)가 모두 있어야 함
         * - 깜빡이 끄기 · 회전 속도 항목은 그에 더해 속도도 있어야 함
         * - 부드러운 주행(급가속 · 급감속)은 속도와 브레이크(급감속 판정)가 모두 있어야 함
         */
        fun measurable(signals: Set<SignalType>): Set<ScoreItem> = buildSet {
            if (SignalType.TURN_SIGNAL in signals && SignalType.STEERING_ANGLE in signals) add(TURN_SIGNAL)
            if (SignalType.TURN_SIGNAL in signals && SignalType.STEERING_ANGLE in signals && SignalType.SPEED in signals) {
                add(SIGNAL_CANCEL)
                add(TURN_SPEED)
            }
            if (SignalType.SPEED in signals && SignalType.BRAKE in signals) add(SMOOTH_DRIVING)
            if (SignalType.SAFETY_WARNING in signals) add(SAFETY_WARNING)
            if (SignalType.HEADLIGHT in signals) add(NIGHT_HEADLIGHT)
        }
    }
}
