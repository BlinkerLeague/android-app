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
import com.example.blinkerleague.domain.config.Coordinate
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.BrakeSample
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.HeadlightSample
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnSignalState
import com.example.blinkerleague.ui.result.toPlaysUiState
import com.example.blinkerleague.ui.result.toResultUiState
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
import java.time.ZonedDateTime
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
    fun `시연 시나리오 결과는 C · 평점 50 · 득점 1 · 실점 1, 플레이 카드 2장`() = runTest {
        // given
        // 시연 시나리오를 0ms(가상 시간)부터 재생해 기록하고 업로드까지 마친 결과
        val s = Scenarios.demo
        val recorder = recorder(s)
        backgroundScope.launch { recorder.run() }
        advanceTimeBy(s.durationMs + 1_000)
        val finished = recorder.state.value as RecorderState.Finished
        repository.uploadPending()
        val scored = repository.observeResult(finished.tripId).first()!!

        // when
        val result = scored.toResultUiState(null, GradeThreshold.DEFAULT)
        val plays = scored.toPlaysUiState(ZoneId.of("Asia/Seoul"))

        // then
        assertEquals("C", result.grade)
        assertEquals(50, result.score)
        assertEquals(1, result.goals)
        assertEquals(1, result.conceded)
        assertEquals(listOf(true, false), plays.plays.map { it.goal })
        assertEquals(1, plays.goals)
        assertEquals(1, plays.conceded)
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

    @Test
    fun `운행 중 브레이크 샘플을 모으고 운행 전 상태를 이어받아 급감속을 저장한다`() = runTest {
        // given
        // 0s에 브레이크를 밟은 채 2초 정차 → 2s 출발, 30km/h로 10초 → 15km/h로 10초(1초에 15km/h 감속) → 종료
        // 브레이크는 운행 시작 전에 한 번 밟은 뒤 상태 변화가 없음
        val s = scenario("브레이크 밟은 채 출발 후 급감속") {
            stop(seconds = 2)
            start()
            straight(seconds = 10, speedKmh = 30f)
            straight(seconds = 10, speedKmh = 15f)
            end()
        }.let {
            it.copy(
                supported = it.supported + SignalType.SPEED + SignalType.BRAKE,
                brakes = listOf(BrakeSample(0, pressed = true)),
            )
        }
        val recorder = recorder(s)
        backgroundScope.launch { recorder.run() }

        // when
        advanceTimeBy(s.durationMs + 1_000)

        // then
        val finished = recorder.state.value as RecorderState.Finished
        val types = db.tripDao().trip(finished.tripId)!!.events.map { it.type }
        assertTrue("저장된 이벤트: $types", "HARSH_BRAKE" in types)
    }

    @Test
    fun `운행 중 하향등 샘플을 모아 야간 운행에서 NIGHT_SEGMENT를 저장한다`() = runTest {
        // given
        // 2026-11-15 22:00 KST(밤)에 시나리오 시작, 서울 대표 좌표 · Asia/Seoul
        // 2초 정차 → 출발, 30km/h로 60초 → 종료. 하향등은 운행 시작 전에 꺼진 뒤 상태 변화가 없음
        val zone = ZoneId.of("Asia/Seoul")
        val nightStartMs = ZonedDateTime.of(2026, 11, 15, 22, 0, 0, 0, zone).toInstant().toEpochMilli()
        val s = scenario("밤에 하향등 끈 채 운행") {
            stop(seconds = 2)
            start()
            straight(seconds = 60, speedKmh = 30f)
            end()
        }.let {
            it.copy(
                supported = it.supported + SignalType.HEADLIGHT,
                headlights = listOf(HeadlightSample(0, on = false)),
            )
        }
        val recorder = TripRecorder(
            vehicle = FakeVehicleSignalSource(s, startMs = nightStartMs),
            location = FakeLocationSource(s, startMs = nightStartMs),
            repository = repository,
            config = DetectionConfig.DEFAULT,
            clock = { nightStartMs + testScheduler.currentTime },
            newId = { "id-${++seq}" },
            coordinate = { Coordinate(37.57, 126.98) },
            zone = zone,
        )
        backgroundScope.launch { recorder.run() }

        // when
        advanceTimeBy(s.durationMs + 1_000)

        // then
        val finished = recorder.state.value as RecorderState.Finished
        val types = db.tripDao().trip(finished.tripId)!!.events.map { it.type }
        assertTrue("저장된 이벤트: $types", "NIGHT_SEGMENT" in types)
    }
}
