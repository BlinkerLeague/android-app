package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.TripConfig
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.VehicleStateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSegmenterTest {

    private val segmenter = TripSegmenter(TripConfig(parkEndMinutes = 3))
    private val threeMin = 180_000L

    private fun state(t: Long, on: Boolean?, gear: Gear) =
        segmenter.onVehicleState(VehicleStateSample(t, on, gear))

    @Test
    fun `시동 on이면 시작, off면 종료`() {
        assertEquals(TripBoundary.Started(0), state(0, true, Gear.P))
        assertNull(state(1_000, true, Gear.D))
        assertEquals(TripBoundary.Ended(60_000, EndReason.IGNITION_OFF), state(60_000, false, Gear.P))
        assertFalse(segmenter.inTrip)
    }

    @Test
    fun `P단 3분 유지되면 P단에 들어간 시각으로 종료`() {
        state(0, true, Gear.D)
        state(50_000, true, Gear.P)
        assertNull(segmenter.onTick(50_000 + threeMin - 1))
        assertEquals(TripBoundary.Ended(50_000, EndReason.PARKED), segmenter.onTick(50_000 + threeMin))
    }

    @Test
    fun `P단 3분이 되기 전에 출발하면 운행 유지`() {
        state(0, true, Gear.D)
        state(50_000, true, Gear.P)
        assertNull(state(100_000, true, Gear.D))
        assertNull(segmenter.onTick(50_000 + threeMin))
        assertTrue(segmenter.inTrip)
    }

    @Test
    fun `P단 종료 후 시동이 켜진 채로는 새 운행을 시작하지 않고, 기어를 바꾸면 시작`() {
        state(0, true, Gear.D)
        state(10_000, true, Gear.P)
        segmenter.onTick(10_000 + threeMin)
        assertFalse(segmenter.inTrip)

        assertNull(state(300_000, true, Gear.P))
        assertEquals(TripBoundary.Started(310_000), state(310_000, true, Gear.D))
    }

    @Test
    fun `상태 샘플로도 P단 시간을 확인함`() {
        state(0, true, Gear.D)
        state(10_000, true, Gear.P)
        assertEquals(TripBoundary.Ended(10_000, EndReason.PARKED), state(10_000 + threeMin, true, Gear.P))
    }

    @Test
    fun `기어를 모르면 시동으로만 구분`() {
        assertEquals(TripBoundary.Started(0), state(0, true, Gear.UNKNOWN))
        assertNull(segmenter.onTick(10 * threeMin))
        assertEquals(TripBoundary.Ended(999_000, EndReason.IGNITION_OFF), state(999_000, false, Gear.UNKNOWN))
    }

    @Test
    fun `시동을 모르면(Pleos SDK) 기어 D로 시작하고 P단 3분으로 끝남`() {
        assertNull(state(0, null, Gear.P))
        assertEquals(TripBoundary.Started(10_000), state(10_000, null, Gear.D))
        assertNull(state(60_000, null, Gear.P))
        assertNull(segmenter.onTick(60_000 + threeMin - 1))
        assertEquals(TripBoundary.Ended(60_000, EndReason.PARKED), segmenter.onTick(60_000 + threeMin))
    }

    @Test
    fun `시동을 모를 때 기어를 알 수 없으면 시작하지 않음`() {
        assertNull(state(0, null, Gear.UNKNOWN))
        assertFalse(segmenter.inTrip)
    }

    @Test
    fun `운행 중이 아닐 때 시동 off는 무시`() {
        assertNull(state(0, false, Gear.P))
        assertNull(segmenter.onTick(threeMin))
    }
}
