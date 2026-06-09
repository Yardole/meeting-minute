package com.oliva.notes.app.domain.model

import java.util.UUID

data class TranscriptSegment(
    val id: UUID,
    val meetingId: UUID,
    val speakerId: UUID? = null,
    val speakerName: String = "Unknown",
    val text: String,
    val startTimeMs: Int,
    val endTimeMs: Int,
    val confidence: Float? = null
)
