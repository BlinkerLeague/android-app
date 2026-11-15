package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.model.SpeedSample

/** 속도 시계열에서 시간을 재는 공통 계산. 순수 함수 */
object SpeedDuration {

    /**
     * [startMs, endMs] 중 [minSpeedKmh] 이상으로 달린 시간(ms).
     * 샘플 사이는 앞 샘플 값이 유지된다고 봄 (TripDistance.kmFromSpeeds와 같은 방식)
     */
    fun millisAtOrAbove(speeds: List<SpeedSample>, minSpeedKmh: Float, startMs: Long, endMs: Long): Long {
        val sorted = speeds.sortedBy { it.timestampMs }
        var millis = 0L
        for ((i, s) in sorted.withIndex()) {
            if (s.speedKmh < minSpeedKmh) continue
            val from = maxOf(s.timestampMs, startMs)
            val to = minOf(sorted.getOrNull(i + 1)?.timestampMs ?: endMs, endMs)
            if (to > from) millis += to - from
        }
        return millis
    }
}
