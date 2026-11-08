package com.example.blinkerleague.ui.theme

import androidx.compose.ui.graphics.Color

// UI 목업 "디자인 토큰"(11.08 개정) 기준 팔레트. 값을 바꿀 때는 목업과 plan.md "목업 개정 반영"도 같이 고침

// 바탕
internal val Background = Color(0xFF0B1020)
internal val Surface = Color(0xFF141A2B) // 패널
val InputSurface = Color(0xFF0E1424) // 입력칸·하단 바·버튼 안쪽
internal val Outline = Color(0xFF232B40) // 구분선·패널 테두리
internal val OutlineStrong = Color(0xFF2A3247) // 진행 막대 바탕

// 글자
internal val TextPrimary = Color(0xFFF3F5F7)
internal val TextSecondary = Color(0xFF9AA3B5)
val TextDisabled = Color(0xFF5E677A) // 비활성·측정 제외 항목

// 강조·브랜드 (주 버튼, 진행 링)
internal val Accent = Color(0xFFFBB024)
internal val OnAccent = Color(0xFF1A1200)

// 판정 색: 득점·양호 / 실점·최저 / 등급·정보
// 초록·빨강은 색만으로 구분하지 않음: 득점/실점 글자와 방향 화살표를 항상 같이 씀
val Score = Color(0xFF3DDC84)
val Lose = Color(0xFFFF5A5F)
val Info = Color(0xFF4DA3FF)

// 판정 결과 칩·카드 바탕 (목업 타임라인 카드 바탕색)
val SuccessContainer = Color(0xFF0F2A1E)
val OnSuccessContainer = Score
val WarningContainer = Color(0xFF2D1216)
val OnWarningContainer = Lose
