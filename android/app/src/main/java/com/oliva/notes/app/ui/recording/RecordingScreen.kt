package com.oliva.notes.app.ui.recording

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun RecordingScreen(
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    onNavigateToMeeting: (String) -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val processingStatus by viewModel.processingStatus.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val isError by viewModel.isError.collectAsState()
    val isQueued by viewModel.isQueued.collectAsState()
    val navigateToMeetingId by viewModel.navigateToMeetingId.collectAsState()
    val tooShort by viewModel.tooShort.collectAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Haptic: one pulse per ripple wave — first 5 seconds of recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val start = System.currentTimeMillis()
            while (isActive && System.currentTimeMillis() - start < 5_000) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                delay(700)
            }
        }
    }

    // Haptic: recording stopped, processing begins
    var wasProcessing by remember { mutableStateOf(false) }
    LaunchedEffect(processingStatus) {
        val nowProcessing = processingStatus.isNotEmpty()
        if (nowProcessing && !wasProcessing) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
        wasProcessing = nowProcessing
    }

    // Auto-navigate when processing completes — with success haptic
    LaunchedEffect(navigateToMeetingId) {
        navigateToMeetingId?.let { id ->
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            viewModel.onNavigated()
            onNavigateToMeeting(id)
        }
    }

    // Show toast for too-short recordings and go back
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(tooShort) {
        if (tooShort) {
            android.widget.Toast.makeText(
                context,
                "Recording too short — at least 10 seconds needed",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            delay(600)
            onNavigateHome()
        }
    }

    if (processingStatus.isNotEmpty()) {
        ProcessingScreen(
            status = processingStatus,
            progress = processingProgress,
            isError = isError,
            isQueued = isQueued,
            onRetry = { viewModel.retryProcessing() },
            onViewMeeting = {
                navigateToMeetingId?.let { onNavigateToMeeting(it) }
            },
            onNavigateHome = onNavigateHome
        )
    } else {
        RecordingActiveScreen(
            elapsedMs = elapsedMs,
            onStartRecording = { viewModel.startRecording() },
            onStopRecording = { viewModel.stopRecording() },
        )
    }
}

@Composable
private fun ProcessingScreen(
    status: String,
    progress: Float,
    isError: Boolean,
    isQueued: Boolean,
    onRetry: () -> Unit,
    onViewMeeting: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val dots = listOf(
        rememberInfiniteTransition(label = "dot1").let { transition ->
            transition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "alpha1"
            )
        },
        rememberInfiniteTransition(label = "dot2").let { transition ->
            transition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, delayMillis = 300), RepeatMode.Reverse),
                label = "alpha2"
            )
        },
        rememberInfiniteTransition(label = "dot3").let { transition ->
            transition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, delayMillis = 600), RepeatMode.Reverse),
                label = "alpha3"
            )
        },
    )

    // Show "Process in background" after 5 seconds
    var showBackgroundButton by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(5_000)
        showBackgroundButton = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Queued icon
            if (isQueued) {
                Text(
                    text = "📡",
                    fontSize = 28.sp
                )
            }
            // Animated dots — stop on error or queued
            else if (!isError) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dots.forEach { alpha ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = alpha.value)
                                )
                        )
                    }
                }
            } else {
                // Error icon — simple ⚠ emoji in warm error color
                Text(
                    text = "⚠",
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val statusColor = if (isError)
                com.oliva.notes.app.ui.theme.WarmError
            else
                MaterialTheme.colorScheme.onBackground

            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                ),
                color = statusColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Subtle progress track
            val progressColor = if (isError)
                com.oliva.notes.app.ui.theme.WarmError
            else
                MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .padding(horizontal = 80.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(progressColor)
                )
            }

            if (isError) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Retry button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(com.oliva.notes.app.ui.theme.WarmOlive)
                            .clickable { onRetry() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    // View meeting button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { onViewMeeting() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "View meeting",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (isQueued) {
                Spacer(modifier = Modifier.height(24.dp))
                // View meeting button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onViewMeeting() }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "View meeting",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // "Process in background" button at bottom — fades in after 5s
        if (showBackgroundButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onNavigateHome() }
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Process in background",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun RecordingActiveScreen(
    elapsedMs: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            onStartRecording()
        }
    }

    if (!hasPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Microphone permission required",
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ripple waves + pulsing record button
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                RecordingRipples(
                    color = MaterialTheme.colorScheme.error,
                    baseSizeDp = 140
                )

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable { onStopRecording() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = formatElapsed(elapsedMs),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Tap to stop",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            )
        }
    }
}

@Composable
private fun RecordingRipples(
    color: androidx.compose.ui.graphics.Color,
    baseSizeDp: Int
) {
    val rippleTransition = rememberInfiniteTransition(label = "ripples")

    val rippleCount = 3
    val cycleMs = 2100

    for (i in 0 until rippleCount) {
        val delayMs = i * (cycleMs / rippleCount)

        val scale by rippleTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 4.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(cycleMs, easing = LinearEasing, delayMillis = delayMs),
                repeatMode = RepeatMode.Restart
            ),
            label = "rippleScale$i"
        )

        val alpha by rippleTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(cycleMs, easing = LinearEasing, delayMillis = delayMs),
                repeatMode = RepeatMode.Restart
            ),
            label = "rippleAlpha$i"
        )

        Box(
            modifier = Modifier
                .size(baseSizeDp.dp)
                .scale(scale)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = color.copy(alpha = alpha),
                    shape = CircleShape
                )
        )
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
