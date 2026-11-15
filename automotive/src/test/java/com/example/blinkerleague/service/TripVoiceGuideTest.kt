package com.example.blinkerleague.service

import com.example.blinkerleague.domain.config.TripConfig
import com.example.blinkerleague.domain.config.TurnConfig
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnRecord
import com.example.blinkerleague.voice.VoiceAnnouncer
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TripVoiceGuideTest {

    private class RecordingAnnouncer : VoiceAnnouncer {
        val spoken = mutableListOf<String>()
        override suspend fun speak(text: String): Boolean {
            spoken += text
            return true
        }
    }

    private val announcer = RecordingAnnouncer()
    private val mission = TodayMission("회전 깜빡이 완벽", MissionStatus.IN_PROGRESS, 0, 0, 3)
    private val result = TripResult("t1", 0, 0, 5.0, ScoreState.PENDING, null, listOf(TurnRecord(0, TurnDirection.LEFT, true)))

    private fun TestScope.guide() = TripVoiceGuide(
        announcer = announcer,
        todayMission = { mission },
        tripResult = { id -> result.takeIf { id == "t1" } },
        tripConfig = { TripConfig() },
        scope = this,
    )

    @Test
    fun `운행당 시동 시 1회, 종료 시 1회만 말함`() = runTest {
        val guide = guide()
        guide.onRecorderState(RecorderState.Idle)
        guide.onRecorderState(RecorderState.Recording("t1", 0))
        guide.onRecorderState(RecorderState.Recording("t1", 0))
        guide.onRecorderState(RecorderState.Finished("t1"))
        guide.onRecorderState(RecorderState.Finished("t1"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                "오늘의 퀘스트, 회전 깜빡이 완벽. 이번 운행에서 회전 세 번 모두 깜빡이를 미리 켜 보세요.",
                "경기 종료! 1 대 0 퍼펙트 승리입니다!",
            ),
            announcer.spoken,
        )
    }

    @Test
    fun `종료 안내는 기준값 turn signalLeadSec을 쓴다`() = runTest {
        // given
        // 깜빡이를 켜지 않은 회전 하나(0 대 1), 기준값은 회전 7초 전
        val conceded = result.copy(turns = listOf(TurnRecord(0, TurnDirection.LEFT, false)))
        val guide = TripVoiceGuide(
            announcer = announcer,
            todayMission = { mission },
            tripResult = { conceded },
            tripConfig = { TripConfig() },
            scope = this,
            turnConfig = { TurnConfig(signalLeadSec = 7) },
        )

        // when
        guide.onRecorderState(RecorderState.Finished("t1"))
        advanceUntilIdle()

        // then
        assertEquals(listOf("경기 종료. 이번엔 아쉬웠어요. 다음 경기에선 회전 7초 전에 깜빡이!"), announcer.spoken)
    }

    @Test
    fun `다음 운행은 다시 안내함`() = runTest {
        val guide = guide()
        guide.onRecorderState(RecorderState.Recording("t1", 0))
        guide.onRecorderState(RecorderState.Recording("t2", 100))
        advanceUntilIdle()
        assertEquals(2, announcer.spoken.size)
    }

    @Test
    fun `운행 결과를 찾지 못하면 종료 안내를 하지 않음`() = runTest {
        val guide = guide()
        guide.onRecorderState(RecorderState.Finished("unknown"))
        advanceUntilIdle()
        assertEquals(emptyList<String>(), announcer.spoken)
    }
}
