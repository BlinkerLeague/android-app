package com.example.blinkerleague.service

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.blinkerleague.data.local.AppDatabase
import com.example.blinkerleague.data.local.UploadState
import com.example.blinkerleague.data.location.FakeLocationSource
import com.example.blinkerleague.data.remote.FakeTripApi
import com.example.blinkerleague.data.repository.TripMapper
import com.example.blinkerleague.data.repository.TripRepository
import com.example.blinkerleague.data.scenario.Scenario
import com.example.blinkerleague.data.scenario.Scenarios
import com.example.blinkerleague.data.scenario.scenario
import com.example.blinkerleague.data.vehicle.FakeVehicleSignalSource
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnSignalState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripRecorderTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TripRepository
    private var seq = 0

    @Before
    fun setUp() {
        val direct = Executor { it.run() } // 가상 시간 테스트에서 Room 작업을 같은 스레드에서 바로 실행
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .build()
        repository = TripRepository(db.tripDao(), FakeTripApi(), TripMapper(ZoneId.of("Asia/Seoul"))) { 0L }
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.recorder(s: Scenario) = TripRecorder(
        vehicle = FakeVehicleSignalSource(s),
        location = FakeLocationSource(s),
        repository = repository,
        config = DetectionConfig.DEFAULT,
        clock = { testScheduler.currentTime },
        newId = { "id-${++seq}" },
    )

    @Test
    fun `시연 시나리오 - 시동 off로 운행이 끝나면 회전 판정과 함께 저장됨`() = runTest {
        val s = Scenarios.demo
        val recorder = recorder(s)
        backgroundScope.launch { recorder.run() }

        advanceTimeBy(1_500)
        val recording = recorder.state.value as RecorderState.Recording
        assertEquals(0L, recording.startedAtMs)

        advanceTimeBy(s.durationMs + 1_000)
        val finished = recorder.state.value as RecorderState.Finished
        assertEquals(recording.tripId, finished.tripId)

        val result = repository.observeResult(finished.tripId).first()!!
        assertEquals(ScoreState.PENDING, result.scoreState) // 업로드는 기록과 별도
        assertEquals(
            listOf(TurnDirection.LEFT to true, TurnDirection.RIGHT to false),
            result.turns.map { it.direction to it.signalOn },
        )
        assertTrue(result.distanceKm > 1.0)
        assertEquals(s.durationMs, result.endedAtMs)

        repository.uploadPending()
        val scored = repository.observeResult(finished.tripId).first()!!
        assertEquals(ScoreState.SCORED, scored.scoreState)
        assertEquals(50, scored.score)
    }

    @Test
    fun `P단 3분이 지나면 시계 틱으로 운행 종료, 끝 시각은 P단에 들어간 시각`() = runTest {
        val s = scenario("P단 정차") {
            start()
            straight(seconds = 30, speedKmh = 40f)
            gear(Gear.P)
            stop(seconds = 1)
        }
        val parkedAt = s.vehicleStates.last().timestampMs
        val recorder = recorder(s)
        backgroundScope.launch { recorder.run() }

        advanceTimeBy(parkedAt + 179_000)
        assertTrue(recorder.state.value is RecorderState.Recording)

        advanceTimeBy(2_000)
        val finished = recorder.state.value as RecorderState.Finished
        val trip = db.tripDao().trip(finished.tripId)!!.trip
        assertEquals(parkedAt, trip.endedAtMs)
        assertEquals(UploadState.PENDING, trip.uploadState)
    }

    @Test
    fun `운행 시작 전에 켜 둔 깜빡이도 이어받아 판정함`() = runTest {
        val s = scenario("출발 전 깜빡이") {
            signal(TurnSignalState.LEFT)
            straight(seconds = 2, speedKmh = 0f)
            start()
            turn(angleDeg = -90f, seconds = 5, speedKmh = 15f)
            signal(TurnSignalState.OFF)
            straight(seconds = 10, speedKmh = 30f)
            end()
        }
        val recorder = recorder(s)
        backgroundScope.launch { recorder.run() }
        advanceTimeBy(s.durationMs + 1_000)

        val finished = recorder.state.value as RecorderState.Finished
        val turn = repository.observeResult(finished.tripId).first()!!.turns.single()
        assertEquals(true, turn.signalOn)
    }
}
