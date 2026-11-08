package com.example.blinkerleague.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingStatusTest {

    @Test
    fun `기록 흐름 상태를 화면용 기록 상태로 바꿈`() {
        assertEquals(RecordingStatus.WAITING, RecorderState.Idle.toStatus())
        assertEquals(RecordingStatus.RECORDING, RecorderState.Recording("t", 0).toStatus())
        assertEquals(RecordingStatus.WAITING, RecorderState.Finished("t").toStatus()) // 다음 운행을 기다림
    }
}
