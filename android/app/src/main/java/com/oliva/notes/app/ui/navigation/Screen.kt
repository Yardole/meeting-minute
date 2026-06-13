package com.oliva.notes.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute : NavKey

@Serializable
data object HomeRoute : NavKey

@Serializable
data class RecordingRoute(val sessionId: Long = System.nanoTime()) : NavKey

@Serializable
data class MeetingDetailRoute(val meetingId: String) : NavKey
