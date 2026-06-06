package com.meetingminute.app.ui.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.meetingminute.app.ui.home.HomeScreen
import com.meetingminute.app.ui.meetingdetail.MeetingDetailScreen
import com.meetingminute.app.ui.recording.RecordingScreen

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope
) {
    with(sharedTransitionScope) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    animatedVisibilityScope = this@composable,
                    sharedTransitionScope = sharedTransitionScope,
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
                    animatedVisibilityScope = this@composable,
                    sharedTransitionScope = sharedTransitionScope,
                    onNavigateToMeeting = { meetingId ->
                        navController.popBackStack()
                        navController.navigate(Screen.MeetingDetail.createRoute(meetingId))
                    },
                    onNavigateHome = {
                        navController.popBackStack()
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
}
