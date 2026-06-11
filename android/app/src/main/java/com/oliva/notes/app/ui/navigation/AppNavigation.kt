package com.oliva.notes.app.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.hilt.navigation.compose.hiltViewModel
import com.oliva.notes.app.ui.auth.AuthViewModel
import com.oliva.notes.app.ui.auth.LoginScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.oliva.notes.app.ui.home.HomeScreen
import com.oliva.notes.app.ui.meetingdetail.MeetingDetailScreen
import com.oliva.notes.app.ui.meetingdetail.MeetingDetailViewModel
import com.oliva.notes.app.ui.recording.RecordingScreen

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(
    sharedTransitionScope: SharedTransitionScope,
    authViewModel: AuthViewModel,
) {
    with(sharedTransitionScope) {
        Box(modifier = Modifier.fillMaxSize()) {
            val authState by authViewModel.state.collectAsState()
            val backStack = remember { mutableStateListOf<NavKey>(LoginRoute) }

            // Navigate to home when logged in
            LaunchedEffect(authState.isLoggedIn) {
                if (authState.isLoggedIn && backStack.lastOrNull() !is HomeRoute) {
                    backStack.clear()
                    backStack.add(HomeRoute)
                }
            }

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = { key: NavKey ->
                    when (key) {
                        is LoginRoute -> NavEntry(key) {
                            LoginScreen(
                                authViewModel = authViewModel,
                                onLoggedIn = { },
                            )
                        }

                        is HomeRoute -> NavEntry(key) {
                            val avs = LocalNavAnimatedContentScope.current
                            HomeScreen(
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = avs,
                                onMeetingClick = { meetingId ->
                                    backStack.add(MeetingDetailRoute(meetingId))
                                },
                                onRecordClick = {
                                    backStack.add(RecordingRoute)
                                },
                            )
                        }

                        is RecordingRoute -> NavEntry(key) {
                            RecordingScreen(
                                sharedTransitionScope = sharedTransitionScope,
                                onNavigateToMeeting = { meetingId ->
                                    backStack.removeLastOrNull()
                                    backStack.add(MeetingDetailRoute(meetingId))
                                },
                                onNavigateHome = {
                                    backStack.removeLastOrNull()
                                }
                            )
                        }

                        is MeetingDetailRoute -> NavEntry(key) {
                            val avs = LocalNavAnimatedContentScope.current
                            val viewModel: MeetingDetailViewModel = hiltViewModel(
                                creationCallback = { factory: MeetingDetailViewModel.Factory ->
                                    factory.create(key)
                                }
                            )
                            MeetingDetailScreen(
                                meetingId = key.meetingId,
                                onBackClick = { backStack.removeLastOrNull() },
                                viewModel = viewModel,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = avs,
                            )
                        }

                        else -> error("Unknown route: $key")
                    }
                },
                predictivePopTransitionSpec = { _ ->
                    fadeIn(tween(700)) togetherWith fadeOut(tween(700))
                }
            )

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
