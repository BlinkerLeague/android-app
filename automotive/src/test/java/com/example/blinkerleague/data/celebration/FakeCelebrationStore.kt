package com.example.blinkerleague.data.celebration

/** 메모리에만 기억하는 가짜. 같은 인스턴스를 새 ViewModel에 넘기면 "앱을 다시 켠 뒤"와 같음 */
class FakeCelebrationStore(vararg celebrated: String) : CelebrationStore {
    private val keys = celebrated.toMutableSet()

    override fun isCelebrated(key: String): Boolean = key in keys

    override fun markCelebrated(key: String) {
        keys += key
    }
}
