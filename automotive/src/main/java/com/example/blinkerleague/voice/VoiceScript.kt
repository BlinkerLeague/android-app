package com.example.blinkerleague.voice

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
        mission == null -> "회전할 때 깜빡이를 미리 켜 주세요."
        mission.status == MissionStatus.ACHIEVED -> "오늘의 퀘스트는 이미 달성했어요. 오늘도 깜빡이 잊지 마세요."
        // 진행 중·지난 날 미달성 모두: 퀘스트는 운행 1건 안에서 판정하므로(서버 D25) 이번 운행에서 처음부터 셈
        else -> "오늘의 퀘스트, ${mission.title}. 이번 운행에서 회전 ${count(mission.requiredTurns)} 모두 깜빡이를 미리 켜 보세요."
    }

    /** 운행 종료 시 1회: 이번 운행 결과 (기기에서 판정한 회전 기준, 서버 점수를 기다리지 않음) */
    fun tripEnd(result: TripResult, minDistanceKm: Double): String {
        val total = result.turns.size
        val success = result.turns.count { it.signalOn }
        return when {
            result.distanceKm < minDistanceKm -> "경기 종료. 짧은 이동이라 경기에 반영되지 않아요."
            total == 0 -> "경기 종료. 오늘은 회전 없는 경기였어요."
            success == total -> "경기 종료! $success 대 0 퍼펙트 승리입니다!"
            success > total - success -> "경기 종료! $success 대 ${total - success} 승리입니다. 화면에서 하이라이트를 볼 수 있어요."
            success == 0 -> "운행이 끝났어요. 다음 회전에는 깜빡이를 미리 켜 봐요."
            else -> "운행이 끝났어요. 회전 ${count(total)} 중 ${count(success)} 깜빡이를 제때 켰어요."
        }
    }

    private val NATIVE = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")

    /** 3 → "세 번" */
    fun count(n: Int): String = if (n in 1..10) "${NATIVE[n]} 번" else "${n}번"
}
