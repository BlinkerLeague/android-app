package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.Turn

/** 회전 구간의 속도 계산 (점수 항목 TURN_SPEED, 서버 TURN payload `maxSpeedKmh`) */
object TurnSpeed {

    /**
     * 회전 구간 최고 속도(km/h). 알 수 없으면 null.
     * 샘플은 다음 샘플까지 유지된다고 보므로, 회전 시작 때 유지 중인 샘플(시작 시각 이전 마지막 1개)도 포함함
     */
    fun maxKmh(turn: Turn, speeds: List<SpeedSample>): Double? {
        val heldAtStart = speeds.filter { it.timestampMs <= turn.startMs }.maxByOrNull { it.timestampMs }
        val during = speeds.filter { it.timestampMs in turn.startMs..turn.endMs }
        return (listOfNotNull(heldAtStart) + during).maxOfOrNull { it.speedKmh.toDouble() }
    }
}
