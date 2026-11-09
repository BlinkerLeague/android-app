package com.example.blinkerleague.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.blinkerleague.AppLog
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.data.repository.UploadSummary
import java.util.concurrent.TimeUnit

/**
 * 업로드 대기열 재전송 작업 (plan.md 3단계 "재전송 트리거").
 *
 * - 네트워크가 연결됐을 때만 실행됨. 운행이 끝났을 때 오프라인이어도 연결되면 자동으로 올라감
 * - 대기열이 남으면(네트워크 오류·5xx 등) 지수 백오프로 다시 시도. 거부(4xx)된 운행은 대기열에서 빠져 있으므로 다시 보내지 않음
 * - WorkManager가 앱 프로세스를 깨워 실행하므로 앱이 동결된 백그라운드여도 동작함
 */
class TripUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as BlinkerLeagueApplication).container.tripRepository
        val summary = repository.uploadPending()
        AppLog.i(TAG, "업로드 시도 #${runAttemptCount + 1}: $summary")
        return when (UploadRetryPolicy.decide(summary, runAttemptCount)) {
            UploadRetryPolicy.Decision.DONE -> Result.success()
            UploadRetryPolicy.Decision.RETRY -> Result.retry()
            UploadRetryPolicy.Decision.GIVE_UP -> {
                AppLog.w(TAG, "재시도 ${UploadRetryPolicy.MAX_ATTEMPTS}회 초과 — 다음 운행 종료·앱 시작 때 다시 예약됨")
                Result.success()
            }
        }
    }

    companion object {
        private const val TAG = "TripUpload"
        private const val UNIQUE_NAME = "trip-upload"

        /**
         * 재전송 작업 예약. 이미 예약·실행 중이면 그 뒤에 이어 붙임(실행 중에 끝난 운행도 빠지지 않게).
         * 운행 종료와 앱 시작 때 부름
         */
        fun enqueue(context: Context, reason: String) {
            val request = OneTimeWorkRequestBuilder<TripUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, UploadRetryPolicy.BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            // 예약에 실패해도(예: WorkManager 미초기화) 앱이 죽지 않게 함. 대기열은 남아 있어 다음 예약 때 올라감
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            }
                .onSuccess { AppLog.i(TAG, "업로드 예약 ($reason)") }
                .onFailure { AppLog.w(TAG, "업로드 예약 실패 ($reason): $it") }
        }
    }
}

/** 업로드 결과로 재시도 여부를 정함 (순수 함수, 단위 테스트 대상) */
object UploadRetryPolicy {
    /** 지수 백오프 시작 간격 (30초 → 1분 → 2분 …, WorkManager 최대 5시간) */
    const val BACKOFF_SECONDS = 30L

    /** 한 번 예약된 작업이 재시도하는 최대 횟수. 넘으면 멈추고 다음 예약을 기다림 */
    const val MAX_ATTEMPTS = 10

    enum class Decision { DONE, RETRY, GIVE_UP }

    /** @param attempt 지금까지 실패한 횟수 (WorkManager `runAttemptCount`, 첫 실행은 0) */
    fun decide(summary: UploadSummary, attempt: Int): Decision = when {
        summary.remaining == 0 -> Decision.DONE
        attempt + 1 >= MAX_ATTEMPTS -> Decision.GIVE_UP
        else -> Decision.RETRY
    }
}
