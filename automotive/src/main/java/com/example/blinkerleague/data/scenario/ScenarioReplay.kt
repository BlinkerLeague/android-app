package com.example.blinkerleague.data.scenario

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 시나리오 시계열을 실제 시간 흐름에 맞춰 내보냄. [speed]가 2면 2배속.
 * 내보내는 값의 타임스탬프는 [retime]으로 바꿈 (예: 시작 시각을 더해 현재 시각 기준으로).
 */
internal fun <T> replay(
    items: List<T>,
    timeOf: (T) -> Long,
    speed: Float,
    retime: (T) -> T,
): Flow<T> = flow {
    var prevMs = 0L
    for (item in items.sortedBy(timeOf)) {
        val waitMs = ((timeOf(item) - prevMs) / speed).toLong()
        if (waitMs > 0) delay(waitMs)
        prevMs = timeOf(item)
        emit(retime(item))
    }
}
