package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.local.TripDao
import com.example.blinkerleague.data.local.TripEntity
import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.TripApi
import com.example.blinkerleague.domain.model.Trip
import kotlinx.coroutines.flow.Flow

data class UploadSummary(
    val uploaded: Int,
    val rejected: Int,
    /** 아직 올리지 못해 대기열에 남은 운행 수 */
    val remaining: Int,
)

/**
 * 운행을 먼저 로컬에 저장하고, 대기열에 쌓인 운행을 서버로 올림.
 *
 * - 저장과 업로드를 분리해서, 운행이 끝난 순간 네트워크가 없어도 기록을 잃지 않음
 * - tripId·eventId가 클라이언트 UUID라서 같은 운행을 다시 올려도 서버에서 중복되지 않음.
 *   그래서 업로드 중 앱이 죽어 PENDING이 남아도 다음에 그냥 다시 보내면 됨
 * - 4xx(인증·타임아웃·요청 과다 제외)는 다시 보내도 같은 결과라 REJECTED로 멈춤
 */
class TripRepository(
    private val dao: TripDao,
    private val api: TripApi,
    private val mapper: TripMapper,
    private val clock: () -> Long,
) {
    suspend fun saveFinishedTrip(trip: Trip) {
        dao.insertTripWithEvents(mapper.toEntity(trip), mapper.toEventEntities(trip))
    }

    fun observeTrip(tripId: String): Flow<TripEntity?> = dao.observeTrip(tripId)

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    /** 대기열을 오래된 순서로 올림. 네트워크 오류가 나면 남은 것은 다음 시도로 미룸 */
    suspend fun uploadPending(): UploadSummary {
        val pending = dao.pendingTrips()
        var uploaded = 0
        var rejected = 0
        for (stored in pending) {
            val tripId = stored.trip.tripId
            when (val result = api.uploadTrip(mapper.toUploadRequest(stored))) {
                is ApiResult.Success -> {
                    dao.markUploaded(tripId, result.body.status, result.body.score, clock())
                    uploaded++
                }
                is ApiResult.HttpError -> if (isRetryable(result.code)) {
                    dao.markAttemptFailed(tripId, clock())
                } else {
                    dao.markRejected(tripId, clock())
                    rejected++
                }
                is ApiResult.NetworkError -> {
                    dao.markAttemptFailed(tripId, clock())
                    break
                }
            }
        }
        return UploadSummary(uploaded, rejected, remaining = pending.size - uploaded - rejected)
    }

    private fun isRetryable(code: Int): Boolean =
        code >= 500 || code == 401 || code == 408 || code == 429
}
