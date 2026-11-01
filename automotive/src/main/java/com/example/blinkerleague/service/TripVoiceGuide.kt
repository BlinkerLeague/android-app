package com.example.blinkerleague.service

import com.example.blinkerleague.domain.config.TripConfig
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.voice.VoiceAnnouncer
import com.example.blinkerleague.voice.VoiceScript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 운행 상태에 맞춰 음성 안내를 냄. 주행 안전 원칙: **운행당 시동 시 1회(미션 안내), 종료 시 1회(결과)** 만.
 * 같은 운행에서 상태가 여러 번 들어와도 한 번만 말함.
 *
 * 안내는 [scope](앱 수명)에서 실행하므로, 기록 작업이 끝나 취소돼도 종료 안내가 끊기지 않음.
 */
class TripVoiceGuide(
    private val announcer: VoiceAnnouncer,
    private val todayMission: suspend () -> TodayMission?,
    private val tripResult: suspend (tripId: String) -> TripResult?,
    private val tripConfig: TripConfig,
    private val scope: CoroutineScope,
) {
    private val startAnnounced = mutableSetOf<String>()
    private val endAnnounced = mutableSetOf<String>()

    fun onRecorderState(state: RecorderState) {
        when (state) {
            is RecorderState.Recording -> if (startAnnounced.add(state.tripId)) {
                scope.launch { announcer.speak(VoiceScript.tripStart(todayMission())) }
            }
            is RecorderState.Finished -> if (endAnnounced.add(state.tripId)) {
                scope.launch {
                    val result = tripResult(state.tripId) ?: return@launch
                    announcer.speak(VoiceScript.tripEnd(result, tripConfig.minDistanceKm))
                }
            }
            RecorderState.Idle -> Unit
        }
    }
}
