package com.oliva.notes.app.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import androidx.hilt.navigation.compose.hiltViewModel
import com.oliva.notes.app.ui.auth.AuthViewModel
import com.oliva.notes.app.ui.auth.LoginScreen
import com.oliva.notes.app.ui.home.HomeScreen
import com.oliva.notes.app.ui.meetingdetail.MeetingDetailScreen
import com.oliva.notes.app.ui.meetingdetail.MeetingDetailViewModel
import com.oliva.notes.app.ui.recording.RecordingScreen
import com.oliva.notes.app.ui.settings.SettingsScreen

private val fastFade = fadeIn(tween(300)) togetherWith fadeOut(tween(300))

private fun slideOverForward(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(tween<IntOffset>(400)) { it },
    initialContentExit = fadeOut(tween(400), targetAlpha = 0.99f),
    targetContentZIndex = 1f,
    sizeTransform = SizeTransform(clip = false),
)

private fun slideOverBack(): ContentTransform = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = slideOutHorizontally(tween<IntOffset>(400)) { it },
    targetContentZIndex = -1f,
    sizeTransform = SizeTransform(clip = false),
)

private val instantSwap = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = ExitTransition.None,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(
    sharedTransitionScope: SharedTransitionScope,
    authViewModel: AuthViewModel,
    pendingMeetingId: StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    onMeetingIdConsumed: () -> Unit = {},
) {
    val authState by authViewModel.state.collectAsState()
    var currentScreen by remember { mutableStateOf<Any>(LoginRoute) }

    val meetingIdToOpen by pendingMeetingId.collectAsState()
    LaunchedEffect(meetingIdToOpen, authState.isLoggedIn) {
        val id = meetingIdToOpen ?: return@LaunchedEffect
        if (authState.isLoggedIn) {
            currentScreen = MeetingDetailRoute(id)
            onMeetingIdConsumed()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled by the OS */ }

    // Navigate to home when logged in (skip if a pending meeting ID will handle navigation)
    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn && currentScreen !is HomeRoute && meetingIdToOpen == null) {
            currentScreen = HomeRoute
        }
        if (authState.isLoggedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val backProgress = remember { Animatable(0f) }
    var gestureNavigation by remember { mutableStateOf(false) }

    // Reset gesture progress when entering a new slide-over screen
    LaunchedEffect(currentScreen) {
        if (currentScreen is MeetingDetailRoute || currentScreen is SettingsRoute) {
            backProgress.snapTo(0f)
        }
    }

    // Predictive back for slide-over screens (detail, settings)
    PredictiveBackHandler(
        enabled = currentScreen is MeetingDetailRoute || currentScreen is SettingsRoute,
    ) { progress ->
        try {
            progress.collect { backEvent ->
                backProgress.snapTo(backEvent.progress)
            }
            // Animate detail fully off-screen, then swap invisibly
            backProgress.animateTo(1f, tween(200))
            gestureNavigation = true
            currentScreen = HomeRoute
        } catch (e: CancellationException) {
            backProgress.animateTo(0f)
            throw e
        }
    }

    // Regular back for recording (has its own back handling in RecordingScreen)
    BackHandler(currentScreen is RecordingRoute) {
        currentScreen = HomeRoute
    }

    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    with(sharedTransitionScope) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Pre-compose home screen behind slide-over screens; reveal during predictive back
            if (currentScreen is MeetingDetailRoute || currentScreen is SettingsRoute) {
                Box(modifier = Modifier.graphicsLayer(alpha = if (backProgress.value > 0f) 1f else 0f)) {
                    HomeScreen(
                        sharedTransitionScope = sharedTransitionScope,
                        onMeetingClick = {},
                        onRecordClick = {},
                        onSettingsClick = {},
                    )
                }
            }

            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    when {
                        // Home → Detail: slide over forward
                        initialState is HomeRoute && targetState is MeetingDetailRoute ->
                            slideOverForward()

                        // Detail/Settings → Home: skip if gesture already handled it
                        (initialState is MeetingDetailRoute || initialState is SettingsRoute)
                                && targetState is HomeRoute ->
                            if (gestureNavigation) instantSwap else slideOverBack()

                        // Home → Settings: slide over forward
                        initialState is HomeRoute && targetState is SettingsRoute ->
                            slideOverForward()

                        // All other transitions: simple fade (recording, login, etc.)
                        else -> fastFade
                    }
                },
                label = "app_nav",
            ) { screen ->
                SideEffect { gestureNavigation = false }
                when (screen) {
                    is LoginRoute -> LoginScreen(
                        authViewModel = authViewModel,
                        onLoggedIn = { currentScreen = HomeRoute },
                    )

                    is HomeRoute -> HomeScreen(
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = this@AnimatedContent,
                        onMeetingClick = { meetingId ->
                            currentScreen = MeetingDetailRoute(meetingId)
                        },
                        onRecordClick = {
                            currentScreen = RecordingRoute()
                        },
                        onSettingsClick = {
                            currentScreen = SettingsRoute
                        },
                    )

                    is MeetingDetailRoute -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    translationX = backProgress.value * screenWidthPx,
                                ),
                        ) {
                            val viewModel: MeetingDetailViewModel = hiltViewModel(
                                creationCallback = { factory: MeetingDetailViewModel.Factory ->
                                    factory.create(screen)
                                }
                            )
                            MeetingDetailScreen(
                                meetingId = screen.meetingId,
                                onBackClick = { currentScreen = HomeRoute },
                                viewModel = viewModel,
                            )
                        }
                    }

                    is SettingsRoute -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    translationX = backProgress.value * screenWidthPx,
                                ),
                        ) {
                            SettingsScreen(
                                onBackClick = { currentScreen = HomeRoute },
                                onSignOut = {
                                    authViewModel.logout()
                                    currentScreen = LoginRoute
                                },
                            )
                        }
                    }

                    is RecordingRoute -> RecordingScreen(
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = this@AnimatedContent,
                        onNavigateToMeeting = { meetingId ->
                            currentScreen = MeetingDetailRoute(meetingId)
                        },
                        onNavigateHome = {
                            currentScreen = HomeRoute
                        },
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
