package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.SeatBeltConfig
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.SeatBeltSample
import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.VehicleStateSample

/** 운행당 안전벨트 판정 한 건 (이벤트 SEAT_BELT). [occurredAtMs]는 출발 시각 */
data class SeatBeltResult(
    val occurredAtMs: Long,
    val buckledAtDeparture: Boolean,
    val unbuckledMovingSec: Int,
)

/**
 * 출발 시 안전벨트 착용 여부와 주행 중 미착용 시간을 판정함. 순수 함수.
 * 출발 시각은 운행 구간 안의 첫 P → D/R 전환, 없으면 운행 시작.
 */
class SeatBeltJudge(private val config: SeatBeltConfig) {

    fun judge(
        seatBelts: List<SeatBeltSample>,
        speeds: List<SpeedSample>,
        vehicleStates: List<VehicleStateSample>,
        tripStartMs: Long,
        tripEndMs: Long,
    ): SeatBeltResult? {
        val departureMs = departureMs(vehicleStates, tripStartMs, tripEndMs)
        val buckled = seatBelts.filter { it.timestampMs <= departureMs }.maxByOrNull { it.timestampMs }?.buckled ?: false
        return SeatBeltResult(occurredAtMs = departureMs, buckledAtDeparture = buckled, unbuckledMovingSec = 0)
    }

    /** 운행 구간 안의 첫 P → D/R 전환 시각. 없으면 운행 시작 */
    private fun departureMs(vehicleStates: List<VehicleStateSample>, tripStartMs: Long, tripEndMs: Long): Long =
        vehicleStates.sortedBy { it.timestampMs }
            .zipWithNext()
            .firstOrNull { (prev, cur) ->
                prev.gear == Gear.P && (cur.gear == Gear.D || cur.gear == Gear.R) && cur.timestampMs in tripStartMs..tripEndMs
            }
            ?.second?.timestampMs
            ?: tripStartMs
}
