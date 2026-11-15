package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.Coordinate
import com.example.blinkerleague.domain.model.HeadlightSample
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
    ): List<NightSegment> = emptyList()
}
