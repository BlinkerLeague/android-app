package com.example.blinkerleague.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.blinkerleague.domain.config.GradeThreshold

/**
 * 등급 이름("S" 등)이 아니라 기준값에서의 순서로 색을 고름. 서버가 등급 이름이나 개수를 바꿔도 맨 위 등급은 금색.
 * 목록에 없는 등급 · 등급 없음(null)은 회색.
 */
fun gradeColor(grade: String?, grades: List<GradeThreshold>): Color {
    val rank = grades.sortedByDescending { it.minScore }.indexOfFirst { it.grade == grade }
    return if (grade == null || rank < 0) TextSecondary else GradeColors.getOrElse(rank) { TextSecondary }
}
