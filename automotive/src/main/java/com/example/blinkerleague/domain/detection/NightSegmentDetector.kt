package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.Coordinate
import com.example.blinkerleague.domain.model.HeadlightSample
import java.time.Instant
import java.time.ZoneId

/** 야간 주행 구간 한 건 (이벤트 NIGHT_SEGMENT). [occurredAtMs]는 구간 시작 시각 */
data class NightSegment(
    val occurredAtMs: Long,
    val durationSec: Int,
    val headlightOffSec: Int,
)

/**
 * 운행 구간 중 일몰 ~ 일출 사이(야간) 구간과, 그 동안 하향등이 꺼져 있던 시간을 구함. 순수 함수.
 * 일출 · 일몰은 등록 지역의 대표 좌표로 계산하고, 날짜는 주입받은 [zone] 기준.
 */
class NightSegmentDetector(private val zone: ZoneId) {

    fun detect(
        headlights: List<HeadlightSample>,
        tripStartMs: Long,
        tripEndMs: Long,
        coordinate: Coordinate,
    ): List<NightSegment> {
        if (tripEndMs <= tripStartMs) return emptyList()
        val sorted = headlights.sortedBy { it.timestampMs }
        return nightIntervals(tripStartMs, tripEndMs, coordinate).map { (start, end) ->
            NightSegment(
                occurredAtMs = start,
                durationSec = ((end - start) / 1000).toInt(),
                headlightOffSec = (offMillis(sorted, start, end) / 1000).toInt(),
            )
        }
    }

    /** 운행 구간과 야간(일몰 ~ 다음 날 일출)이 겹치는 구간들. 운행 시작 전날 밤부터 확인함 */
    private fun nightIntervals(tripStartMs: Long, tripEndMs: Long, coordinate: Coordinate): List<Pair<Long, Long>> {
        val firstDate = Instant.ofEpochMilli(tripStartMs).atZone(zone).toLocalDate().minusDays(1)
        val lastDate = Instant.ofEpochMilli(tripEndMs).atZone(zone).toLocalDate()
        val intervals = mutableListOf<Pair<Long, Long>>()
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            val today = SunTimes.of(date, coordinate.latitude, coordinate.longitude, zone)
            val tomorrow = SunTimes.of(date.plusDays(1), coordinate.latitude, coordinate.longitude, zone)
            if (today != null && tomorrow != null) {
                val start = maxOf(today.sunset.toInstant().toEpochMilli(), tripStartMs)
                val end = minOf(tomorrow.sunrise.toInstant().toEpochMilli(), tripEndMs)
                if (end > start) intervals += start to end
            }
            date = date.plusDays(1)
        }
        return intervals
    }

    /**
     * [start, end) 동안 하향등이 꺼져 있던 시간. 구간 시작 시점의 상태는 그 전 마지막 샘플을 이어 씀.
     * 첫 샘플 전처럼 상태를 모르는 시간은 꺼짐으로 세지 않음.
     */
    private fun offMillis(sorted: List<HeadlightSample>, start: Long, end: Long): Long {
        var state: Boolean? = sorted.lastOrNull { it.timestampMs <= start }?.on
        var cursor = start
        var off = 0L
        for (sample in sorted) {
            if (sample.timestampMs <= start) continue
            if (sample.timestampMs >= end) break
            if (state == false) off += sample.timestampMs - cursor
            state = sample.on
            cursor = sample.timestampMs
        }
        if (state == false) off += end - cursor
        return off
    }
}
