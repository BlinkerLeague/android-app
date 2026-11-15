package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.Turn

/** 회전 구간의 속도 계산 (점수 항목 TURN_SPEED, 서버 TURN payload `maxSpeedKmh`) */
object TurnSpeed {

    /** 회전 구간 최고 속도(km/h). 알 수 없으면 null */
    fun maxKmh(turn: Turn, speeds: List<SpeedSample>): Double? = null
}
