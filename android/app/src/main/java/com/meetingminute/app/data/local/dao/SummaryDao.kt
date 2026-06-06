package com.meetingminute.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.meetingminute.app.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE meetingId = :meetingId AND deletedAt IS NULL")
    fun observeByMeetingId(meetingId: UUID): Flow<SummaryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: SummaryEntity)

    @Update
    suspend fun update(summary: SummaryEntity)

    @Query("SELECT * FROM summaries WHERE deletedAt IS NULL AND updatedAt > :since")
    suspend fun getChangedSince(since: Long): List<SummaryEntity>
}
