package com.oliva.notes.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val title: String = "",
    val durationMs: Int = 0,
    val recordedAt: Instant = Instant.now(),
    val localAudioPath: String? = null,
    val audioUrl: String? = null,
    val audioBucketPath: String? = null,
    val status: MeetingStatus = MeetingStatus.RECORDING,
    val language: String = "en",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)
