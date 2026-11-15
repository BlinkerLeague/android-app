package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.SeatBeltConfig
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.SeatBeltSample
import com.example.blinkerleague.domain.model.VehicleStateSample
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SeatBeltJudgeTest {

    private val seoulZone = ZoneId.of("Asia/Seoul")

    private fun kst(hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(2026, 11, 15, hour, minute, second, 0, seoulZone).toInstant().toEpochMilli()

    @Test
    fun `출발 시각 직전 벨트 상태로 buckledAtDeparture를 정한다`() {
        // given
        // 운행 시작 10:00:00 (기어 P), 10:00:10에 P → D로 출발. 운행 끝 10:10:00
        // 벨트: 09:59:30 미착용(운행 전 상태) → 10:00:05 착용 (출발 5초 전)
        val judge = SeatBeltJudge(SeatBeltConfig())
        val seatBelts = listOf(
            SeatBeltSample(kst(9, 59, 30), buckled = false),
            SeatBeltSample(kst(10, 0, 5), buckled = true),
        )
        val vehicleStates = listOf(
            VehicleStateSample(kst(10, 0, 0), ignitionOn = true, gear = Gear.P),
            VehicleStateSample(kst(10, 0, 10), ignitionOn = true, gear = Gear.D),
        )

        // when
        val result = judge.judge(
            seatBelts = seatBelts,
            speeds = emptyList(),
            vehicleStates = vehicleStates,
            tripStartMs = kst(10, 0, 0),
            tripEndMs = kst(10, 10, 0),
        )

        // then
        assertEquals(true, result?.buckledAtDeparture)
    }
}
