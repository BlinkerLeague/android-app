package com.example.blinkerleague.domain.model

/** 한 운행을 경기로 볼 때의 승패. 득점 = 깜빡이를 켠 회전, 실점 = 켜지 않은 회전. */
enum class MatchOutcome {
    PERFECT,
    WIN,
    DRAW,
    TOUGH,
    ;

    companion object {
        /** 회전이 없으면(0:0) 승패가 없으므로 null. */
        fun of(goals: Int, conceded: Int): MatchOutcome? = WIN
    }
}
