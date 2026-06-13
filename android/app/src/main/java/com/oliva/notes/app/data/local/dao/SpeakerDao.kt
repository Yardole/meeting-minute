package com.oliva.notes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oliva.notes.app.data.local.entity.SpeakerEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SpeakerDao {
    @Query("SELECT * FROM speakers WHERE meetingId = :meetingId AND deletedAt IS NULL ORDER BY displayOrder ASC")
    fun observeByMeetingId(meetingId: UUID): Flow<List<SpeakerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(speakers: List<SpeakerEntity>)

    @Update
    suspend fun update(speaker: SpeakerEntity)

    @Query("SELECT * FROM speakers WHERE meetingId = :meetingId AND deletedAt IS NULL ORDER BY displayOrder ASC")
    suspend fun getByMeetingId(meetingId: UUID): List<SpeakerEntity>

    @Query("SELECT * FROM speakers WHERE deletedAt IS NULL AND updatedAt > :since")
    suspend fun getChangedSince(since: Long): List<SpeakerEntity>

    @Query("SELECT * FROM speakers")
    suspend fun getAllForSync(): List<SpeakerEntity>

    @Query("DELETE FROM speakers WHERE meetingId = :meetingId")
    suspend fun hardDeleteByMeetingId(meetingId: UUID)

    @Query("DELETE FROM speakers")
    suspend fun deleteAll()

    @Query("UPDATE speakers SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE meetingId = :meetingId AND deletedAt IS NULL")
    suspend fun softDeleteByMeetingId(meetingId: UUID, deletedAt: Long, updatedAt: Long)
}
