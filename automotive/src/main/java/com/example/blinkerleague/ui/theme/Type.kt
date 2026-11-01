package com.example.blinkerleague.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 차량 화면은 멀리서 보므로 목업처럼 전체적으로 크게 잡음 (본문 20sp 이상)
internal val BlinkerTypography = Typography(
    displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 20.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 18.sp),
)
