package com.example.blinkerleague.domain.model

/**
 * 측정 항목에 쓰는 차량 신호. 업로드 시 지원하는 것만 `supported` 목록에 넣음.
 * 이름은 서버와 맞춰야 함 (plan.md "서버와 맞춰야 할 항목"). 전체 목록은 스파이크 9번 결과로 확정.
 */
enum class SignalType { TURN_SIGNAL, BRAKE, HEADLIGHT, SAFETY_WARNING }
