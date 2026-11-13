package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.local.TripDao
import com.example.blinkerleague.data.local.UploadState
import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.TripApi
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.domain.model.MetricDetail
import com.example.blinkerleague.domain.model.Trip
import com.example.blinkerleague.domain.model.TripResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.ZoneId

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
 * - 업로드는 결과 화면·재전송 작업([com.example.blinkerleague.data.sync.TripUploadWorker]) 등 여러 곳에서 불리므로
 *   한 번에 하나만 돌게 막음 (같은 운행을 동시에 두 번 보내지 않게)
 */
class TripRepository(
    private val dao: TripDao,
    private val api: TripApi,
    private val mapper: TripMapper,
    private val clock: () -> Long,
) {
    private val uploadLock = Mutex()

    suspend fun saveFinishedTrip(trip: Trip) {
        dao.insertTripWithEvents(mapper.toEntity(trip), mapper.toEventEntities(trip))
    }

    /** 정차 후 결과 화면용. 업로드가 끝나면 점수가 채워진 값이 다시 나옴 */
    fun observeResult(tripId: String): Flow<TripResult?> =
        dao.observeTripWithEvents(tripId).map { it?.let(mapper::toResult) }

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    /**
     * 결과 화면용: 서버에 올라갔지만 점수가 아직 없으면(`UPLOADED`) `GET /api/trips/{id}/score`로 다시 받음.
     * 업로드 전·이미 점수가 있는 운행은 그대로 둠. 실패하면 조용히 넘어감
     */
    suspend fun refreshScore(tripId: String) {
        val stored = dao.trip(tripId) ?: return
        if (stored.trip.uploadState != UploadState.UPLOADED) return
        if (stored.trip.scoreStatus != null && stored.trip.scoreStatus != ScoreStatus.UPLOADED) return
        val result = api.getScore(tripId) as? ApiResult.Success ?: return
        if (result.body.status == ScoreStatus.UPLOADED) return
        dao.markUploaded(tripId, result.body.status, result.body.score, clock())
    }

    /** 항목 상세: 서버가 준 이 운행의 항목별 판정 결과. 업로드 전·조회 실패면 null */
    suspend fun metricDetails(tripId: String): List<MetricDetail>? {
        if (dao.trip(tripId)?.trip?.uploadState != UploadState.UPLOADED) return null
        val body = (api.getScore(tripId) as? ApiResult.Success)?.body ?: return null
        return body.metrics.map { m ->
            MetricDetail(
                metric = m.metric,
                status = m.status,
                reason = m.reason,
                score = m.score,
                weight = m.weight,
                counts = m.detail.orEmpty().mapNotNull { (k, v) -> (v as? JsonPrimitive)?.intOrNull?.let { k to it } }.toMap(),
            )
        }
    }

    /** 결과 화면: 이 운행으로 적립된 포인트 (서버 요청 12번 `earnedPoints`). 업로드 전·서버가 안 주면 null */
    suspend fun earnedPoints(tripId: String): Int? {
        if (dao.trip(tripId)?.trip?.uploadState != UploadState.UPLOADED) return null
        return (api.getScore(tripId) as? ApiResult.Success)?.body?.earnedPoints
    }

    /**
     * 결과 화면 머리말 "오늘의 N번째 경기": 기기에 기록된 그날([zone] 기준 시작 날짜) 운행 중 시작 시각 순으로 몇 번째인지.
     * 짧은 이동도 셈. 자정을 넘긴 운행은 시작한 날로 셈. 오늘 운행이 아니면 null
     */
    suspend fun dayOrdinal(tripId: String, zone: ZoneId): Int? {
        val startedAtMs = dao.trip(tripId)?.trip?.startedAtMs ?: return null
        val dayStartMs = Instant.ofEpochMilli(startedAtMs).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.countStartedBetween(dayStartMs, startedAtMs)
    }

    /** 홈 "최근 경기". 기기에 저장된 운행을 먼저 보여주고, [refreshRecentScores]가 서버 점수로 갱신함 */
    fun observeRecentResults(limit: Int): Flow<List<TripResult>> =
        dao.observeRecent(limit).map { list -> list.map(mapper::toResult) }

    /**
     * 서버의 최근 경기 목록(`GET /api/trips?limit=N`)으로 기기 기록의 점수를 갱신함.
     * 서버가 아직 계산 중(`UPLOADED`)이거나 기기에 없는 운행은 건드리지 않음. 실패하면 기기 기록 그대로 둠
     */
    suspend fun refreshRecentScores(limit: Int) {
        val result = api.getRecentTrips(limit) as? ApiResult.Success ?: return
        for (item in result.body) {
            if (item.status == ScoreStatus.UPLOADED) continue
            if (dao.trip(item.tripId) == null) continue
            dao.markUploaded(item.tripId, item.status, item.score, clock())
        }
    }

    /** 대기열을 오래된 순서로 올림. 네트워크 오류가 나면 남은 것은 다음 시도로 미룸 */
    suspend fun uploadPending(): UploadSummary = uploadLock.withLock { uploadPendingLocked() }

    private suspend fun uploadPendingLocked(): UploadSummary {
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
