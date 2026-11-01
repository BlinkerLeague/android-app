package com.example.blinkerleague.voice

import com.example.blinkerleague.domain.model.MissionStatus
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TripResult

/**
 * 음성 안내 문구. 짧게, 칭찬 위주로, 운전을 재촉하는 표현은 쓰지 않음.
 * 숫자는 TTS가 "사 번"처럼 읽지 않게 고유어(한 번, 두 번…)로 바꿈.
 */
object VoiceScript {

    /** 시동 시 1회: 오늘의 미션 안내 */
    fun tripStart(mission: TodayMission?): String = when {
        mission == null -> "회전할 때 깜빡이를 미리 켜 주세요."
        mission.status == MissionStatus.ACHIEVED -> "오늘의 미션은 이미 달성했어요. 오늘도 깜빡이 잊지 마세요."
        mission.status == MissionStatus.FAILED -> "오늘도 회전할 때 깜빡이를 미리 켜 주세요."
        mission.successTurns == 0 ->
            "오늘의 미션, ${mission.title}. 회전 ${count(mission.requiredTurns)} 모두 깜빡이를 미리 켜 보세요."
        else -> {
            val remaining = (mission.requiredTurns - mission.successTurns).coerceAtLeast(1)
            "오늘의 미션, ${mission.title}. 회전 ${count(remaining)} 남았어요. 깜빡이를 미리 켜 주세요."
        }
    }

    /** 운행 종료 시 1회: 이번 운행 결과 (기기에서 판정한 회전 기준, 서버 점수를 기다리지 않음) */
    fun tripEnd(result: TripResult, minDistanceKm: Double): String {
        val total = result.turns.size
        val success = result.turns.count { it.signalOn }
        return when {
            result.distanceKm < minDistanceKm -> "운행이 끝났어요. 짧은 운행이라 점수에는 반영되지 않아요."
            total == 0 -> "운행이 끝났어요. 이번에는 측정할 회전이 없었어요."
            success == total && total == 1 -> "운행이 끝났어요. 회전할 때 깜빡이를 제때 켰어요. 멋져요."
            success == total -> "운행이 끝났어요. 회전 ${count(total)} 모두 깜빡이를 제때 켰어요. 멋져요."
            success == 0 -> "운행이 끝났어요. 다음 회전에는 깜빡이를 미리 켜 봐요."
            else -> "운행이 끝났어요. 회전 ${count(total)} 중 ${count(success)} 깜빡이를 제때 켰어요."
        }
    }

    private val NATIVE = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")

    /** 3 → "세 번" */
    fun count(n: Int): String = if (n in 1..10) "${NATIVE[n]} 번" else "${n}번"
}
