package com.example.blinkerleague.voice

import com.example.blinkerleague.domain.model.MatchOutcome
import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TripResult

/**
 * 음성 안내 문구. 짧게, 칭찬 위주로, 운전을 재촉하는 표현은 쓰지 않음.
 * 숫자는 TTS가 "사 번"처럼 읽지 않게 고유어(한 번, 두 번…)로 바꿈.
 */
object VoiceScript {

    /** 시동 시 1회: 오늘의 퀘스트 안내 */
    fun tripStart(mission: TodayMission?): String = when {
        mission == null -> "깜빡이리그, 경기 시작합니다! 회전할 때 깜빡이를 미리 켜 주세요."
        mission.status == MissionStatus.ACHIEVED -> "경기 시작합니다! 오늘의 퀘스트는 이미 클리어했어요, 이번 경기도 깜빡이 잊지 마세요."
        // 진행 중·지난 날 미달성 모두: 퀘스트는 운행 1건 안에서 판정하므로(서버 D25) 이번 운행에서 처음부터 셈
        else -> "깜빡이리그, 경기 시작합니다! 오늘의 퀘스트는 ${mission.title}, 회전 ${count(mission.requiredTurns)} 모두 득점해 볼까요?"
    }

    /** 운행 종료 시 1회: 이번 운행 결과 (기기에서 판정한 회전 기준, 서버 점수를 기다리지 않음) */
    fun tripEnd(
        result: TripResult,
        minDistanceKm: Double,
        /** 회전 몇 초 전에 깜빡이를 켜야 하는지 (기준값 turn.signalLeadSec) */
        signalLeadSec: Int = 5,
    ): String {
        val goals = result.turns.count { it.signalOn }
        val conceded = result.turns.size - goals
        if (result.distanceKm < minDistanceKm) return "경기 종료. 짧은 이동이라 경기에 반영되지 않아요."
        val tip = "다음 경기에선 회전 ${signalLeadSec}초 전에 깜빡이!"
        return when (MatchOutcome.of(goals, conceded)) {
            null -> "경기 종료. 오늘은 회전 없는 경기였어요."
            MatchOutcome.PERFECT -> "경기 종료! $goals 대 0 퍼펙트 승리입니다!"
            MatchOutcome.WIN -> "경기 종료! $goals 대 $conceded 승리입니다. $HIGHLIGHT"
            MatchOutcome.DRAW -> "경기 종료! $goals 대 $conceded 무승부입니다. $HIGHLIGHT"
            MatchOutcome.TOUGH ->
                if (goals == 0) "경기 종료. 이번엔 아쉬웠어요. $tip" else "경기 종료. $goals 대 $conceded, 아쉬운 경기였어요. $tip"
        }
    }

    private const val HIGHLIGHT = "화면에서 하이라이트를 볼 수 있어요."

    private val NATIVE = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")

    /** 3 → "세 번" */
    fun count(n: Int): String = if (n in 1..10) "${NATIVE[n]} 번" else "${n}번"
}
