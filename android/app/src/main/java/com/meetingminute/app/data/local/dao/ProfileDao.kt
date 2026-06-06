package com.meetingminute.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.meetingminute.app.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeById(id: UUID): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: UUID): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("SELECT * FROM profiles")
    suspend fun getAllForSync(): List<ProfileEntity>

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}
