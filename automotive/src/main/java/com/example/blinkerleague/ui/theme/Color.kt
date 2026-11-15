package com.example.blinkerleague.ui.theme

import androidx.compose.ui.graphics.Color

// UI 목업(1280×720 다크) 기준 팔레트
internal val Background = Color(0xFF101317)
internal val Surface = Color(0xFF1B2027)
internal val Outline = Color(0xFF2C333D)
internal val OutlineStrong = Color(0xFF3A424D)

internal val TextPrimary = Color(0xFFF3F5F7)
internal val TextSecondary = Color(0xFFA9B1BC)

internal val Accent = Color(0xFF5AA2FF)
internal val OnAccent = Color(0xFF0B1420)

// 판정 결과 칩: 성공(파랑) / 실패(주황). 색 구분이 어려운 경우를 고려해 빨강·초록 대신 파랑·주황을 씀
val SuccessContainer = Color(0xFF16324F)
val OnSuccessContainer = Color(0xFF9CC8FF)
val WarningContainer = Color(0xFF3D2A12)
val OnWarningContainer = Color(0xFFF6C07D)

// 게임 연출 (game-ui 목업). 득점 · 실점 색은 위의 파랑 · 주황을 그대로 씀
val Gold = Color(0xFFFFCC4D)
val Silver = Color(0xFFC9D1DB)
val Bronze = Color(0xFFD9925B)
val Flame = Color(0xFFFF7A45)
val GoldContainer = Color(0xFF3A2C08)

// 등급색: 기준값(config.grades)에서 minScore가 높은 순서로 금 · 하늘 · 민트 · 보라, 그 아래나 등급 없음은 회색
private val GradeSky = Color(0xFF7CC4FF)
private val GradeMint = Color(0xFF8BE0B4)
private val GradePurple = Color(0xFFC9B6FF)
internal val GradeColors = listOf(Gold, GradeSky, GradeMint, GradePurple)
