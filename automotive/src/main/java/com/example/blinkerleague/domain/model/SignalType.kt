package com.example.blinkerleague.domain.model

/**
 * 측정 항목에 쓰는 차량 신호. 업로드 시 지원하는 것만 `supported` 목록에 넣음.
 * 이름은 서버와 맞춰야 함 (plan.md "서버와 맞춰야 할 항목"). 전체 목록은 스파이크 9번 결과로 확정.
 */
enum class SignalType {
    TURN_SIGNAL,

    /** 핸들 각도. 회전 감지에 씀 — 깜빡이 측정은 TURN_SIGNAL과 이것이 모두 있어야 가능 */
    STEERING_ANGLE,
    BRAKE,
    HEADLIGHT,
    SAFETY_WARNING,

    /** 운전석 안전벨트 착용 상태 */
    SEAT_BELT,

    /** 차량 속도. 깜빡이 켜 둔 채 잊음(signalCancel) 판정에 씀. 업로드 supported(ScoreItem)로는 나가지 않음 */
    SPEED,
}
