package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.model.SpeedSample
import com.example.blinkerleague.domain.model.Turn
import com.example.blinkerleague.domain.model.TurnDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TurnSpeedTest {

    @Test
    fun `회전 구간의 최고 속도를 회전 시작 직전 샘플까지 포함해 구한다`() {
        // given
        // 회전 40s ~ 45s. 39.5s 샘플(35)은 회전 시작 때 유지 중인 값이라 포함,
        // 30s(60)는 39.5s 샘플로 바뀌었고 46s(50)는 회전 뒤라 제외
        val turn = Turn(40_000, 45_000, TurnDirection.LEFT, 90f, uTurn = false)
        val speeds = listOf(
            SpeedSample(30_000, 60f),
            SpeedSample(39_500, 35f),
            SpeedSample(41_000, 32f),
            SpeedSample(43_000, 25f),
            SpeedSample(46_000, 50f),
        )

        // when
        val max = TurnSpeed.maxKmh(turn, speeds)

        // then
        assertEquals(35.0, max)
    }
}
