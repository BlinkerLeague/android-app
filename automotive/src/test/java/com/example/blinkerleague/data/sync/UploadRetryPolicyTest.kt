package com.example.blinkerleague.data.sync

import com.example.blinkerleague.data.repository.UploadSummary
import com.example.blinkerleague.data.sync.UploadRetryPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadRetryPolicyTest {

    @Test
    fun `대기열이 비면 끝, 남으면 재시도`() {
        assertEquals(Decision.DONE, UploadRetryPolicy.decide(UploadSummary(uploaded = 2, rejected = 0, remaining = 0), 0))
        assertEquals(Decision.DONE, UploadRetryPolicy.decide(UploadSummary(uploaded = 0, rejected = 1, remaining = 0), 3))
        assertEquals(Decision.RETRY, UploadRetryPolicy.decide(UploadSummary(uploaded = 0, rejected = 0, remaining = 1), 0))
    }

    @Test
    fun `최대 횟수에 닿으면 멈추고 다음 예약을 기다림`() {
        val stuck = UploadSummary(uploaded = 0, rejected = 0, remaining = 1)
        assertEquals(Decision.RETRY, UploadRetryPolicy.decide(stuck, UploadRetryPolicy.MAX_ATTEMPTS - 2))
        assertEquals(Decision.GIVE_UP, UploadRetryPolicy.decide(stuck, UploadRetryPolicy.MAX_ATTEMPTS - 1))
    }
}
