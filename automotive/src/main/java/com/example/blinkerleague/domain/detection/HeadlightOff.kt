package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.model.HeadlightSample

/** 하향등 시계열에서 꺼진 시간을 재는 공통 계산 (야간 · 우천 구간). 순수 함수 */
object HeadlightOff {

    /**
     * [start, end) 동안 하향등이 꺼져 있던 시간(ms). [sorted]는 시각순이어야 함.
     * 구간 시작 시점의 상태는 그 전 마지막 샘플을 이어 씀. 첫 샘플 전처럼 상태를 모르는 시간은 꺼짐으로 세지 않음.
     */
    fun millis(sorted: List<HeadlightSample>, start: Long, end: Long): Long {
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
