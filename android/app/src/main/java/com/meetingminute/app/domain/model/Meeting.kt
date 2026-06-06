package com.meetingminute.app.domain.model

import com.meetingminute.app.data.local.entity.MeetingStatus
import java.time.Instant
import java.util.UUID

data class Meeting(
    val id: UUID,
    val title: String,
    val durationMs: Int,
    val recordedAt: Instant,
    val status: MeetingStatus,
    val localAudioPath: String? = null,
    val audioUrl: String? = null
)
