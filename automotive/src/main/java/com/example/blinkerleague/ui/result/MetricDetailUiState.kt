package com.example.blinkerleague.ui.result

import com.example.blinkerleague.domain.model.MetricDetail
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.domain.config.GradeThreshold
import java.time.ZoneId
import kotlin.math.roundToInt

/** 항목 상세 (SCR-02b) */
sealed interface MetricDetailUiState {
    data object Loading : MetricDetailUiState

    data object NotFound : MetricDetailUiState

    data class Loaded(
        val item: MetricItem,
        /** 결과 화면과 같은 값 (점수 / 회전 없음 / 준비 중 / 미지원 …) */
        val value: MetricValue,
        /** 서버가 측정하지 못했다고 알려준 이유 (예: SIGNAL_NOT_SUPPORTED) */
        val serverReason: String?,
        /** 깜빡이 판정 근거. 서버 수치가 있으면 그것, 없으면 기기 기록으로 셈 */
        val turnCounts: TurnCounts?,
        /** 이번 운행의 회전 (깜빡이 항목만) */
        val turns: List<PlayCard>,
        val tip: String?,
        /** 서버가 이번 점수에 적용한 가중치 (백분율). 서버가 주지 않으면 null */
        val weightPercent: Int? = null,
    ) : MetricDetailUiState
}

data class TurnCounts(val turns: Int, val onTime: Int, val late: Int, val missed: Int, val fromServer: Boolean)

internal fun TripResult.toMetricDetail(
    item: MetricItem,
    server: List<MetricDetail>?,
    grades: List<GradeThreshold>,
    tips: Map<String, String>,
    zone: ZoneId,
): MetricDetailUiState.Loaded {
    val base = toResultUiState(null, grades)
    val value = base.metrics.first { it.item == item }.value
    val fromServer = server?.firstOrNull { it.metric == item.name }
    val counts = if (item == MetricItem.TURN_SIGNAL) {
        val c = fromServer?.counts.orEmpty()
        if (c.containsKey("turns")) {
            TurnCounts(c["turns"] ?: 0, c["onTime"] ?: 0, c["late"] ?: 0, c["missed"] ?: 0, fromServer = true)
        } else {
            val on = turns.count { it.signalOn }
            TurnCounts(turns.size, on, 0, turns.size - on, fromServer = false)
        }
    } else {
        null
    }
    return MetricDetailUiState.Loaded(
        item = item,
        value = value,
        serverReason = fromServer?.reason,
        turnCounts = counts,
        turns = if (item == MetricItem.TURN_SIGNAL) toPlaysUiState(zone).plays else emptyList(),
        tip = tips[item.name],
        weightPercent = fromServer?.weight?.let { (it * 100).roundToInt() },
    )
}
