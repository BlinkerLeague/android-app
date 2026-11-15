package com.example.blinkerleague.ui.common.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * 정차(기어 P · 정차) 화면이 보이는지. MainActivity가 DrivingGuard 판정(showParkedScreens)으로 채움.
 * false면 반복 연출은 멈춘 상태로, 순차 등장 · 카운트업은 시작하지 않음 (주행 안전 원칙).
 */
val LocalParked = compositionLocalOf { false }

/** 연출을 움직여도 되는지. Preview(Inspection)에서는 최종 상태를 그리므로 false */
@Composable
internal fun motionEnabled(): Boolean = LocalParked.current && !LocalInspectionMode.current
