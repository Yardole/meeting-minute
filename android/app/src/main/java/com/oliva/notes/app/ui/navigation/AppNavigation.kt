package com.oliva.notes.app.ui.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.oliva.notes.app.ui.home.HomeScreen
import com.oliva.notes.app.ui.meetingdetail.MeetingDetailScreen
import com.oliva.notes.app.ui.recording.RecordingScreen

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope
) {
    with(sharedTransitionScope) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
            navController = navController,
            startDestination = Screen.Home.route
        ) {
            composable(Screen.Home.route) {
                var selectedMeetingId by remember { mutableStateOf<String?>(null) }

                Box(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        animatedVisibilityScope = this@composable,
                        sharedTransitionScope = sharedTransitionScope,
                        onMeetingClick = { meetingId ->
                            selectedMeetingId = meetingId
                        },
                        onRecordClick = {
                            navController.navigate(Screen.Recording.route)
                        },
                    )

                    // Detail as overlay — same AnimatedVisibilityScope as the cards
                    selectedMeetingId?.let { id ->
                        MeetingDetailScreen(
                            meetingId = id,
                            onBackClick = { selectedMeetingId = null },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = this@composable
                        )
                    }
                }
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
                    onBackClick = { navController.popBackStack() },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@composable
                )
            }
        }

        // Top scrim — status bar legibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom scrim — nav bar legibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.06f)
                        )
                    )
                )
        )
    }
    }
}
