package com.example.blinkerleague.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// 목업 디자인 토큰: 모서리 패널 18 · 버튼 14 · 입력 12, 패널 안쪽 여백 24, 요소 간격 20
internal val BlinkerShapes = Shapes(
    small = RoundedCornerShape(12.dp), // 입력칸, 칩
    medium = RoundedCornerShape(14.dp), // 버튼
    large = RoundedCornerShape(18.dp), // 패널·카드
)

object BlinkerDimens {
    val PanelPadding = 24.dp
    val Gap = 20.dp
    val ScreenPadding = 28.dp

    /** 버튼·카드 최소 높이 (CLAUDE.md: 터치 영역 64dp 이상) */
    val TouchMin = 64.dp
}
