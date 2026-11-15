package com.example.blinkerleague.domain.model

/**
 * 서버가 준 항목별 판정 결과 (점수 응답 `metrics`). 항목 상세(SCR-02b)에서 씀
 *
 * @property counts 판정 근거 수치 (예: 깜빡이 `turns`, `onTime`, `late`, `missed`). 숫자만 담음
 * @property weight 이 운행 총점에 적용된 가중치 (서버 `metrics[].weight`). 미측정·미지원이면 null
 */
data class MetricDetail(
    val metric: String,
    val status: String?,
    val reason: String?,
    val score: Int?,
    val counts: Map<String, Int>,
    val weight: Double? = null,
)
