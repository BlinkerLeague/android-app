package com.example.blinkerleague.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class UploadState {
    /** 아직 서버에 올리지 못함 (재전송 대상) */
    PENDING,

    /** 업로드 완료. 서버가 준 점수 결과가 같이 저장됨 */
    UPLOADED,

    /** 서버가 요청 자체를 거부함(검증 실패, 다른 운전자의 tripId 등). 재전송해도 같은 결과라 멈춤 */
    REJECTED,
}

@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey val tripId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceKm: Double,
    /** SignalType 이름을 쉼표로 이은 값 */
    val supported: String,
    val uploadState: UploadState = UploadState.PENDING,
    val attemptCount: Int = 0,
    val lastAttemptAtMs: Long? = null,
    /** 서버 점수 상태 (`SCORED`, `NO_TURN`, `EXCLUDED_SHORT`) */
    val scoreStatus: String? = null,
    val score: Int? = null,
)

@Entity(
    tableName = "trip_event",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["tripId"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class TripEventEntity(
    @PrimaryKey val eventId: String,
    val tripId: String,
    /** 서버 이벤트 type (`TURN` 등) */
    val type: String,
    val occurredAtMs: Long,
    /** 서버로 보내는 payload JSON 그대로 */
    val payload: String,
)

data class TripWithEvents(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "tripId", entityColumn = "tripId")
    val events: List<TripEventEntity>,
)
