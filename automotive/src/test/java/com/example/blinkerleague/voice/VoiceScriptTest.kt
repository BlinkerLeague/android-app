package com.example.blinkerleague.voice

import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceScriptTest {

    private fun mission(status: MissionStatus, success: Int) =
        TodayMission("회전 깜빡이 완벽", status, turns = success, successTurns = success, requiredTurns = 3)

    private fun result(distanceKm: Double, vararg signalOn: Boolean) = TripResult(
        tripId = "t", startedAtMs = 0, endedAtMs = 0, distanceKm = distanceKm, scoreState = ScoreState.PENDING, score = null,
        turns = signalOn.map { TurnRecord(0, TurnDirection.LEFT, it) },
    )

    @Test
    fun `시동 안내는 오늘의 퀘스트로 상태별 문구를 말한다`() {
        // given
        val notStarted = mission(MissionStatus.IN_PROGRESS, 0)
        // 퀘스트는 운행 1건 안에서 판정(서버 D25) → 지난 운행 진행도와 상관없이 이번 운행 기준
        val partlyDone = mission(MissionStatus.IN_PROGRESS, 2)
        val achieved = mission(MissionStatus.ACHIEVED, 3)
        val failed = mission(MissionStatus.FAILED, 1)

        // when
        val notStartedScript = VoiceScript.tripStart(notStarted)
        val partlyDoneScript = VoiceScript.tripStart(partlyDone)
        val achievedScript = VoiceScript.tripStart(achieved)
        val failedScript = VoiceScript.tripStart(failed)
        val noQuestScript = VoiceScript.tripStart(null)

        // then
        val questIntro = "오늘의 퀘스트, 회전 깜빡이 완벽. 이번 운행에서 회전 세 번 모두 깜빡이를 미리 켜 보세요."
        assertEquals(questIntro, notStartedScript)
        assertEquals(questIntro, partlyDoneScript)
        assertEquals("오늘의 퀘스트는 이미 달성했어요. 오늘도 깜빡이 잊지 마세요.", achievedScript)
        assertEquals(questIntro, failedScript)
        assertEquals("회전할 때 깜빡이를 미리 켜 주세요.", noQuestScript)
    }

    @Test
    fun `종료 안내 - 결과별 문구`() {
        assertEquals("운행이 끝났어요. 회전 네 번 중 세 번 깜빡이를 제때 켰어요.", VoiceScript.tripEnd(result(5.0, true, true, false, true), 1.0))
        assertEquals("운행이 끝났어요. 회전 두 번 모두 깜빡이를 제때 켰어요. 멋져요.", VoiceScript.tripEnd(result(5.0, true, true), 1.0))
        assertEquals("운행이 끝났어요. 다음 회전에는 깜빡이를 미리 켜 봐요.", VoiceScript.tripEnd(result(5.0, false), 1.0))
        assertEquals("운행이 끝났어요. 이번에는 측정할 회전이 없었어요.", VoiceScript.tripEnd(result(5.0), 1.0))
        assertEquals("운행이 끝났어요. 짧은 운행이라 점수에는 반영되지 않아요.", VoiceScript.tripEnd(result(0.5, true), 1.0))
    }

    @Test
    fun `횟수는 고유어로, 10 넘으면 숫자`() {
        assertEquals("한 번", VoiceScript.count(1))
        assertEquals("열 번", VoiceScript.count(10))
        assertEquals("11번", VoiceScript.count(11))
    }
}
