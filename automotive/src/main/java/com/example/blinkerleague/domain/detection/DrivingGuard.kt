package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.GuardConfig
import com.example.blinkerleague.domain.model.Gear
import kotlin.math.abs

/** 화면을 열어도 되는지 (CLAUDE.md 주행 안전 원칙: 상세 화면은 정차·기어 P일 때만) */
enum class GuardState {
    /** 기어 P이고 멈춰 있음 → 정차 화면 허용 */
    PARKED,

    /** 주행 중으로 판단 → 기록 중 화면만 */
    DRIVING,

    /** 기어 값을 받지 못함 → 안전 쪽으로 화면을 열지 않음 */
    VEHICLE_UNKNOWN,
}

/**
 * 주행 안전 가드 판정 (docs/screen-plan.md 3장 표). 순수 함수.
 *
 * | 기어 | 속도 | 결과 |
 * | P | 기준 이하 또는 측정 안 됨 | PARKED |
 * | P | 기준 초과 | DRIVING (안전 쪽) |
 * | D·R·N | 아무 값 | DRIVING |
 * | UNKNOWN·값 없음 | — | VEHICLE_UNKNOWN |
 *
 * "D단 속도 0"(신호 대기)은 정차로 보지 않음 (screen-plan 9장, P0은 P단만)
 */
object DrivingGuard {
    fun evaluate(gear: Gear?, speedKmh: Float?, config: GuardConfig): GuardState = when (gear) {
        null, Gear.UNKNOWN -> GuardState.VEHICLE_UNKNOWN
        Gear.P -> if (speedKmh == null || abs(speedKmh) <= config.parkedMaxSpeedKmh) GuardState.PARKED else GuardState.DRIVING
        Gear.R, Gear.N, Gear.D -> GuardState.DRIVING
    }
}
