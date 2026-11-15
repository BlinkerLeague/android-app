package com.example.blinkerleague.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    /** 같은 tripId·eventId가 이미 있으면 무시 (멱등) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<TripEventEntity>)

    @Transaction
    suspend fun insertTripWithEvents(trip: TripEntity, events: List<TripEventEntity>) {
        insertTrip(trip)
        insertEvents(events)
    }

    @Transaction
    @Query("SELECT * FROM trip WHERE uploadState = 'PENDING' ORDER BY startedAtMs")
    suspend fun pendingTrips(): List<TripWithEvents>

    @Transaction
    @Query("SELECT * FROM trip WHERE tripId = :tripId")
    suspend fun trip(tripId: String): TripWithEvents?

    @Transaction
    @Query("SELECT * FROM trip WHERE tripId = :tripId")
    fun observeTripWithEvents(tripId: String): Flow<TripWithEvents?>

    /** 최근 운행 (홈 "최근 경기"). 종료 시각 최신순 */
    @Transaction
    @Query("SELECT * FROM trip ORDER BY endedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TripWithEvents>>

    /** 결과 화면 "오늘의 N번째 경기": [dayStartMs]부터 [startedAtMs]까지(포함) 시작한 운행 수 */
    @Query("SELECT COUNT(*) FROM trip WHERE startedAtMs >= :dayStartMs AND startedAtMs <= :startedAtMs")
    suspend fun countStartedBetween(dayStartMs: Long, startedAtMs: Long): Int

    @Query("SELECT COUNT(*) FROM trip WHERE uploadState = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query(
        "UPDATE trip SET uploadState = 'UPLOADED', scoreStatus = :scoreStatus, score = :score, " +
            "attemptCount = attemptCount + 1, lastAttemptAtMs = :atMs WHERE tripId = :tripId",
    )
    suspend fun markUploaded(tripId: String, scoreStatus: String, score: Int?, atMs: Long)

    @Query("UPDATE trip SET attemptCount = attemptCount + 1, lastAttemptAtMs = :atMs WHERE tripId = :tripId")
    suspend fun markAttemptFailed(tripId: String, atMs: Long)

    @Query(
        "UPDATE trip SET uploadState = 'REJECTED', attemptCount = attemptCount + 1, " +
            "lastAttemptAtMs = :atMs WHERE tripId = :tripId",
    )
    suspend fun markRejected(tripId: String, atMs: Long)
}
