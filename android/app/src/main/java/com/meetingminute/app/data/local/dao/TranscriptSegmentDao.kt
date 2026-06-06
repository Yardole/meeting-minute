package com.meetingminute.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meetingminute.app.data.local.entity.TranscriptSegmentEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface TranscriptSegmentDao {
    @Query("SELECT * FROM transcript_segments WHERE meetingId = :meetingId AND deletedAt IS NULL ORDER BY startTimeMs ASC")
    fun observeByMeetingId(meetingId: UUID): Flow<List<TranscriptSegmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<TranscriptSegmentEntity>)

    @Query("SELECT * FROM transcript_segments WHERE meetingId = :meetingId AND deletedAt IS NULL ORDER BY startTimeMs ASC")
    suspend fun getByMeetingId(meetingId: UUID): List<TranscriptSegmentEntity>

    @Query("SELECT * FROM transcript_segments WHERE deletedAt IS NULL AND updatedAt > :since")
    suspend fun getChangedSince(since: Long): List<TranscriptSegmentEntity>

    @Query("SELECT * FROM transcript_segments")
    suspend fun getAllForSync(): List<TranscriptSegmentEntity>

    @Query("DELETE FROM transcript_segments")
    suspend fun deleteAll()
}
