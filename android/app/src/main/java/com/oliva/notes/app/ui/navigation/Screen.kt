package com.oliva.notes.app.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Recording : Screen("recording")
    data object MeetingDetail : Screen("meeting/{meetingId}") {
        fun createRoute(meetingId: String) = "meeting/$meetingId"
    }
}
