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
 * 설정 대상 경고등 종류마다 한 번 켜진 주기(켜짐 → 꺼짐)에 한 건만 셈 (운행 구간 안만).
 * 그 주기에서 처음으로 속도가 movingMinSpeedKmh 이상이 된 시각에 셈. 속도가 기준을 넘나들어도 주기는 끝나지 않고,
 * 경고등 샘플에서 그 종류가 빠져야 주기가 끝남.
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
        // 이번 켜짐 주기에서 이미 센 종류. 경고등이 꺼지면 지움
        val counted = mutableSetOf<String>()
        // 시각 t 이하인 마지막 경고등 · 속도 샘플 위치. times가 오름차순이므로 앞으로만 움직임
        var telltaleIndex = -1
        var speedIndex = -1
        for (t in times) {
            while (telltaleIndex + 1 < sortedTelltales.size && sortedTelltales[telltaleIndex + 1].timestampMs <= t) telltaleIndex++
            while (speedIndex + 1 < sortedSpeeds.size && sortedSpeeds[speedIndex + 1].timestampMs <= t) speedIndex++
            val active = sortedTelltales.getOrNull(telltaleIndex)?.active.orEmpty() intersect config.telltaleTypes
            counted.retainAll(active)
            val moving = (sortedSpeeds.getOrNull(speedIndex)?.speedKmh ?: 0f) >= config.movingMinSpeedKmh
            if (!moving) continue
            (active - counted).sorted().forEach {
                hits += SafetyWarningHit(occurredAtMs = t, type = it)
                counted += it
            }
        }
        return hits
    }
}
