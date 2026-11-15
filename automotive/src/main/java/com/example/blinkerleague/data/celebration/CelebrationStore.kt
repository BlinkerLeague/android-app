package com.example.blinkerleague.data.celebration

/**
 * 이미 축하 연출(꽃가루)을 보여 준 것 기억하기. 앱을 다시 켜도 유지돼야 함 (plan-ui 결정 11: 퀘스트 클리어 축하는 회차당 1회).
 * 키는 화면이 정함 (예: 퀘스트 회차 `missionId:periodKey`)
 */
interface CelebrationStore {
    fun isCelebrated(key: String): Boolean
    fun markCelebrated(key: String)
}
