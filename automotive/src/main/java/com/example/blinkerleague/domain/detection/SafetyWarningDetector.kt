package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.SafetyWarningConfig
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.TelltaleSample

/** 안전 경고등 한 건. [occurredAtMs]는 세는 시각, [type]은 경고등 이름 */
data class SafetyWarningHit(
    val occurredAtMs: Long,
    val type: String,
)

/**
 * 주행 중 켜진 안전 경고등을 셈. 순수 함수.
 * 경고등 종류마다 "설정 대상이고 켜져 있고 속도가 movingMinSpeedKmh 이상"이 거짓 → 참이 되는 시각에 한 건 (운행 구간 안만).
 * 경고등 · 속도 샘플 사이는 앞 샘플 값이 유지된다고 봄.
 */
class SafetyWarningDetector(private val config: SafetyWarningConfig) {

    fun detect(
        telltales: List<TelltaleSample>,
        speeds: List<SpeedSample>,
        tripStartMs: Long,
        tripEndMs: Long,
    ): List<SafetyWarningHit> {
        val sortedTelltales = telltales.sortedBy { it.timestampMs }
        val sortedSpeeds = speeds.sortedBy { it.timestampMs }
        // 상태가 바뀔 수 있는 시각: 운행 시작과 운행 구간 안의 샘플 시각
        val times = (sequenceOf(tripStartMs) + (sortedTelltales.map { it.timestampMs } + sortedSpeeds.map { it.timestampMs })
            .filter { it in tripStartMs..tripEndMs })
            .distinct().sorted().toList()

        val hits = mutableListOf<SafetyWarningHit>()
        var counting = emptySet<String>()
        for (t in times) {
            val active = sortedTelltales.lastOrNull { it.timestampMs <= t }?.active.orEmpty()
            val moving = (sortedSpeeds.lastOrNull { it.timestampMs <= t }?.speedKmh ?: 0f) >= config.movingMinSpeedKmh
            val now = if (moving) active intersect config.telltaleTypes else emptySet()
            (now - counting).sorted().forEach { hits += SafetyWarningHit(occurredAtMs = t, type = it) }
            counting = now
        }
        return hits
    }
}
