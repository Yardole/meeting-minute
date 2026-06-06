package com.meetingminute.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.meetingminute.app.data.local.entity.MeetingEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings WHERE deletedAt IS NULL ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun observeById(id: UUID): Flow<MeetingEntity?>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getById(id: UUID): MeetingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meeting: MeetingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meetings: List<MeetingEntity>)

    @Update
    suspend fun update(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings WHERE deletedAt IS NULL AND updatedAt > :since")
    suspend fun getChangedSince(since: Long): List<MeetingEntity>

    @Query("SELECT * FROM meetings")
    suspend fun getAllForSync(): List<MeetingEntity>

    @Query("DELETE FROM meetings")
    suspend fun deleteAll()
}
