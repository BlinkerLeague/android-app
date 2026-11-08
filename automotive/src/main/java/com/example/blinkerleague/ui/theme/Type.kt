package com.example.blinkerleague.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 목업 디자인 토큰의 글자 단계. 차량 화면은 멀리서 보므로 최소 17sp
//   Display 60/900 · Score 40/700 · Title 28/700 · Body 21/400 · Label 17/400
internal val BlinkerTypography = Typography(
    displayLarge = TextStyle(fontSize = 60.sp, fontWeight = FontWeight.Black), // 앱 이름, 등급 글자
    displayMedium = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold), // 평점 등 핵심 숫자 1개
    headlineMedium = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold), // 상세 화면 제목
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold), // 카드 제목
    bodyLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold), // 버튼
    labelMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal), // 카드 머리말·보조
)
