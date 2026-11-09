package com.example.blinkerleague.domain.model

/**
 * 서버가 준 항목별 판정 결과 (점수 응답 `metrics`). 항목 상세(SCR-02b)에서 씀
 *
 * @property counts 판정 근거 수치 (예: 깜빡이 `turns`, `onTime`, `late`, `missed`). 숫자만 담음
 */
data class MetricDetail(
    val metric: String,
    val status: String?,
    val reason: String?,
    val score: Int?,
    val counts: Map<String, Int>,
)
