package com.meetingminute.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meetingminute.app.ui.home.HomeScreen
import com.meetingminute.app.ui.meetingdetail.MeetingDetailScreen
import com.meetingminute.app.ui.recording.RecordingScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onMeetingClick = { meetingId ->
                    navController.navigate(Screen.MeetingDetail.createRoute(meetingId))
                },
                onRecordClick = {
                    navController.navigate(Screen.Recording.route)
                }
            )
        }
        composable(Screen.Recording.route) {
            RecordingScreen(
                onNavigateToMeeting = { meetingId ->
                    navController.popBackStack()
                    navController.navigate(Screen.MeetingDetail.createRoute(meetingId))
                }
            )
        }
        composable(
            route = Screen.MeetingDetail.route,
            arguments = listOf(
                navArgument("meetingId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
            MeetingDetailScreen(
                meetingId = meetingId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
