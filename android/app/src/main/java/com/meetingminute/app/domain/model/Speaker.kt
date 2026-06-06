package com.meetingminute.app.domain.model

import java.util.UUID

data class Speaker(
    val id: UUID,
    val meetingId: UUID,
    val label: String,
    val name: String? = null,
    val displayOrder: Int = 0
)
