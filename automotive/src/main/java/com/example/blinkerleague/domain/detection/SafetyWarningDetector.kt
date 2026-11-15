package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.SafetyWarningConfig
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.TelltaleSample

/** 안전 경고등 한 건. [occurredAtMs]는 세는 시각, [type]은 경고등 이름 */
data class SafetyWarningHit(
    val occurredAtMs: Long,
    val type: String,
)

/** 주행 중 켜진 안전 경고등을 셈. 순수 함수 */
class SafetyWarningDetector(private val config: SafetyWarningConfig) {

    fun detect(
        telltales: List<TelltaleSample>,
        speeds: List<SpeedSample>,
        tripStartMs: Long,
        tripEndMs: Long,
    ): List<SafetyWarningHit> = emptyList()
}
