package com.ryccoatika.journeyrecorder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class JourneyCount(
    val journeyId: Long,
    val cnt: Int,
)

@Dao
interface JourneyDao {
    // journeys
    @Insert
    suspend fun insertJourney(journey: JourneyEntity): Long

    @Query("UPDATE journeys SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE journeys SET endedAt = :endedAt, status = :status, pausedMs = :pausedMs WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long, status: JourneyStatus, pausedMs: Long)

    @Query("UPDATE journeys SET noElementIds = :noElementIds WHERE id = :id")
    suspend fun setNoElementIds(id: Long, noElementIds: Boolean)

    @Query("DELETE FROM journeys WHERE id = :id")
    suspend fun deleteJourney(id: Long)

    @Query("SELECT * FROM journeys ORDER BY startedAt DESC")
    fun observeJourneys(): Flow<List<JourneyEntity>>

    @Query("SELECT * FROM journeys WHERE id = :id")
    suspend fun getJourney(id: Long): JourneyEntity?

    @Query("SELECT * FROM journeys WHERE id = :id")
    fun observeJourney(id: Long): Flow<JourneyEntity?>

    @Query("SELECT * FROM journeys WHERE endedAt IS NULL")
    suspend fun getUnfinishedJourneys(): List<JourneyEntity>

    // events
    @Insert
    suspend fun insertEvent(event: JourneyEventEntity): Long

    @Query("SELECT * FROM journey_events WHERE journeyId = :journeyId ORDER BY sequence ASC")
    fun observeEvents(journeyId: Long): Flow<List<JourneyEventEntity>>

    @Query("SELECT * FROM journey_events WHERE journeyId = :journeyId ORDER BY sequence ASC")
    suspend fun getEvents(journeyId: Long): List<JourneyEventEntity>

    @Query("SELECT COUNT(*) FROM journey_events WHERE journeyId = :journeyId")
    fun observeEventCount(journeyId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM journey_events WHERE journeyId = :journeyId")
    suspend fun countEvents(journeyId: Long): Int

    @Query("SELECT journeyId, COUNT(*) AS cnt FROM journey_events GROUP BY journeyId")
    fun observeEventCounts(): Flow<List<JourneyCount>>

    @Query("SELECT MAX(wallClockMs) FROM journey_events WHERE journeyId = :journeyId")
    suspend fun getLastEventTime(journeyId: Long): Long?

    @Query("UPDATE journey_events SET masked = 1, typedText = NULL WHERE id = :eventId")
    suspend fun redactEvent(eventId: Long)
}
