package com.meetingminute.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: UUID,
    val displayName: String?,
    val avatarUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
