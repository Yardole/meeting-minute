package com.meetingminute.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meetingminute.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE meetingId = :meetingId AND deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeByMeetingId(meetingId: UUID): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT * FROM chat_messages WHERE deletedAt IS NULL AND updatedAt > :since")
    suspend fun getChangedSince(since: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages")
    suspend fun getAllForSync(): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
