package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.TripEventDto
import com.example.blinkerleague.data.remote.dto.TripUploadRequest
import com.example.blinkerleague.data.remote.dto.TurnPayload
import com.example.blinkerleague.data.repository.MissionRepository
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.TodayMission
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.time.ZoneId

class FakeMissionApiTest {

    private val zone = ZoneId.of("Asia/Seoul")
    // 2026-11-03T10:00:00+09:00
    private var now = 1_793_667_600_000L
    private val tripApi = FakeTripApi()
    private val missionApi = FakeMissionApi(tripApi, { now }, zone, requiredTurns = 3)
    private val repository = MissionRepository(missionApi)
    private var seq = 0

    private suspend fun upload(startedAt: String, vararg signalOn: Boolean, distanceKm: Double = 5.0) {
        val id = "trip-${++seq}"
        tripApi.uploadTrip(
            TripUploadRequest(
                tripId = id,
                startedAt = startedAt,
                endedAt = startedAt,
                distanceKm = distanceKm,
                supported = listOf("TURN_SIGNAL", "STEERING_ANGLE"),
                events = signalOn.mapIndexed { i, on ->
                    TripEventDto("$id-$i", "TURN", startedAt, Json.encodeToJsonElement(TurnPayload("LEFT", on)))
                },
            ),
        )
    }

    private fun mission(status: MissionStatus, turns: Int, success: Int) =
        TodayMission("회전 깜빡이 완벽", status, turns, success, 3, rewardPoint = 20)

    @Test
    fun `운행이 없으면 진행 중, 0회`() = runTest {
        assertEquals(mission(MissionStatus.IN_PROGRESS, 0, 0), repository.todayMission())
    }

    @Test
    fun `운행 1건 안에서 판정 - 여러 운행을 합산하지 않음`() = runTest {
        upload("2026-11-03T08:00:00+09:00", true, true)
        assertEquals(mission(MissionStatus.IN_PROGRESS, 2, 2), repository.todayMission())

        upload("2026-11-03T09:00:00+09:00", true) // 합하면 3회지만 라운드마다 따로 셈
        assertEquals(mission(MissionStatus.IN_PROGRESS, 1, 1), repository.todayMission()) // 가장 최근 라운드

        upload("2026-11-03T09:30:00+09:00", true, true, true)
        assertEquals(mission(MissionStatus.ACHIEVED, 3, 3), repository.todayMission())
    }

    @Test
    fun `실패한 라운드가 있어도 그날은 진행 중, 달성한 뒤 실패해도 달성 유지`() = runTest {
        upload("2026-11-03T08:00:00+09:00", true, false, true, true)
        assertEquals(mission(MissionStatus.IN_PROGRESS, 4, 3), repository.todayMission())

        upload("2026-11-03T08:30:00+09:00", true, true, true)
        upload("2026-11-03T09:00:00+09:00", false)
        assertEquals(mission(MissionStatus.ACHIEVED, 3, 3), repository.todayMission()) // 달성한 라운드 진행도
    }

    @Test
    fun `오늘 운행이 없으면 마지막 미션 날의 최종 상태 - 못 채웠으면 실패`() = runTest {
        upload("2026-11-02T18:00:00+09:00", true, false)
        val yesterday = repository.todayMission()!!
        assertEquals(MissionStatus.FAILED, yesterday.status)
        assertEquals(2, yesterday.turns)

        upload("2026-11-03T08:00:00+09:00", true)
        assertEquals(mission(MissionStatus.IN_PROGRESS, 1, 1), repository.todayMission())
    }

    @Test
    fun `짧은 운행은 집계하지 않고, 날짜는 종료 시각 기준`() = runTest {
        upload("2026-11-03T08:00:00+09:00", false, distanceKm = 0.5)
        upload("2026-11-03T00:00:00+09:00", true)
        assertEquals(mission(MissionStatus.IN_PROGRESS, 1, 1), repository.todayMission())
    }

    @Test
    fun `같은 운행을 다시 올려도 한 번만 셈`() = runTest {
        val request = TripUploadRequest(
            "same", "2026-11-03T08:00:00+09:00", "2026-11-03T08:10:00+09:00", 5.0, emptyList(),
            listOf(TripEventDto("e", "TURN", "2026-11-03T08:01:00+09:00", Json.encodeToJsonElement(TurnPayload("LEFT", true)))),
        )
        tripApi.uploadTrip(request)
        tripApi.uploadTrip(request)
        assertEquals(1, repository.todayMission()!!.turns)
    }

    @Test
    fun `미션을 받지 못하면 null`() = runTest {
        missionApi.failWith = ApiResult.NetworkError(IOException("offline"))
        assertNull(repository.todayMission())
    }
}
