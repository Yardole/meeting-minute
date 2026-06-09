package com.oliva.notes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oliva.notes.app.data.local.entity.TranscriptSegmentEntity
import androidx.room.ColumnInfo
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class TranscriptTextRow(
    @ColumnInfo(name = "meetingId") val meetingId: UUID,
    val text: String
)

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

    @Query("UPDATE transcript_segments SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE meetingId = :meetingId AND deletedAt IS NULL")
    suspend fun softDeleteByMeetingId(meetingId: UUID, deletedAt: Long, updatedAt: Long)

    @Query("SELECT meetingId, text FROM transcript_segments WHERE deletedAt IS NULL ORDER BY startTimeMs ASC")
    fun observeAllForSearch(): Flow<List<TranscriptTextRow>>
}
