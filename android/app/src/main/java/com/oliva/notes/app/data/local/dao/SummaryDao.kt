package com.oliva.notes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oliva.notes.app.data.local.entity.SummaryEntity
import androidx.room.ColumnInfo
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class SummaryTextRow(
    @ColumnInfo(name = "meetingId") val meetingId: UUID,
    val content: String
)

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE meetingId = :meetingId AND deletedAt IS NULL")
    fun observeByMeetingId(meetingId: UUID): Flow<SummaryEntity?>

    @Query("SELECT * FROM summaries WHERE meetingId = :meetingId AND deletedAt IS NULL")
    suspend fun getByMeetingId(meetingId: UUID): SummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: SummaryEntity)

    @Update
    suspend fun update(summary: SummaryEntity)

    @Query("SELECT * FROM summaries WHERE deletedAt IS NULL AND updatedAt > :since")
    suspend fun getChangedSince(since: Long): List<SummaryEntity>

    @Query("SELECT * FROM summaries")
    suspend fun getAllForSync(): List<SummaryEntity>

    @Query("DELETE FROM summaries WHERE meetingId = :meetingId")
    suspend fun hardDeleteByMeetingId(meetingId: UUID)

    @Query("DELETE FROM summaries")
    suspend fun deleteAll()

    @Query("UPDATE summaries SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE meetingId = :meetingId AND deletedAt IS NULL")
    suspend fun softDeleteByMeetingId(meetingId: UUID, deletedAt: Long, updatedAt: Long)

    @Query("SELECT meetingId, content FROM summaries WHERE deletedAt IS NULL")
    fun observeAllForSearch(): Flow<List<SummaryTextRow>>
}
