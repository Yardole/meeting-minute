package com.oliva.notes.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SpeakerEntity::class,
            parentColumns = ["id"],
            childColumns = ["speakerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("meetingId"), Index("speakerId")]
)
data class TranscriptSegmentEntity(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    val meetingId: UUID,
    val speakerId: UUID? = null,
    val text: String,
    val startTimeMs: Int,
    val endTimeMs: Int,
    val confidence: Float? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)
