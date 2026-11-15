package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.SeatBeltConfig
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.SeatBeltSample
import com.example.blinkerleague.domain.model.SpeedSample
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

    @Test
    fun `movingMinSpeedKmh 이상으로 달린 동안 벨트를 안 맨 초를 잰다`() {
        // given
        // 10:00:00 P → 10:00:10 D로 출발, 운행 끝 10:10:00. 출발 때는 벨트 착용
        // 속도: 출발부터 운행 끝까지 1초 간격으로 내내 30km/h (기준 10km/h 이상)
        // 벨트: 10:02:00 미착용 → 10:02:40 다시 착용. 달리면서 안 맨 시간 40초
        val judge = SeatBeltJudge(SeatBeltConfig(movingMinSpeedKmh = 10))
        val seatBelts = listOf(
            SeatBeltSample(kst(9, 59, 30), buckled = true),
            SeatBeltSample(kst(10, 2, 0), buckled = false),
            SeatBeltSample(kst(10, 2, 40), buckled = true),
        )
        val speeds = (kst(10, 0, 10)..kst(10, 10, 0) step 1_000L).map { SpeedSample(it, speedKmh = 30f) }
        val vehicleStates = listOf(
            VehicleStateSample(kst(10, 0, 0), ignitionOn = true, gear = Gear.P),
            VehicleStateSample(kst(10, 0, 10), ignitionOn = true, gear = Gear.D),
        )

        // when
        val result = judge.judge(
            seatBelts = seatBelts,
            speeds = speeds,
            vehicleStates = vehicleStates,
            tripStartMs = kst(10, 0, 0),
            tripEndMs = kst(10, 10, 0),
        )

        // then
        assertEquals(40, result?.unbuckledMovingSec)
    }
}
