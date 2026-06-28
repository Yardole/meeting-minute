package com.oliva.notes.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun BottomPlayer(
    modifier: Modifier = Modifier,
    currentPositionMs: Int = 0,
    durationMs: Int = 0,
    isPlaying: Boolean = false,
    onPlayPause: () -> Unit = {},
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onProgressClick: (Float) -> Unit = {},
    speed: Float = 1.0f,
    onSpeedChange: () -> Unit = {}
) {
    val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f
    val currentTime = formatTime(currentPositionMs)
    val totalTime = formatTime(durationMs)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Everything wrapped in a single rounded card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = totalTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }

            // Progress bar with a gooey capsule playhead — tap or drag to seek.
            GooeyProgressBar(
                progress = progress,
                durationMs = durationMs,
                onProgressClick = onProgressClick,
            )

            // Controls — play/pause dead center, sides equally weighted
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left group: speed + skip back
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${String.format("%.1f", speed)}x",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clickable { onSpeedChange() }
                        )
                        IconButton(
                            onClick = onSeekBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Skip back 10s",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Center: play/pause
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Right group: skip forward (room for one more later)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = onSeekForward,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Skip forward 10s",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Seek bar with a liquid "gooey" capsule playhead.
 *
 * The playhead is a horizontal pill that grows on touch-down, stretches like a
 * blob while dragging, and springs back to a clean pill on release. The stretch
 * is pure-Compose kinematic physics (no shader): a lagging [anchor] chases the
 * [visual] head via a manually-stepped damped spring run in a `withFrameNanos`
 * loop, and the capsule is drawn spanning the gap between them.
 */
@Composable
private fun GooeyProgressBar(
    progress: Float,
    durationMs: Int,
    onProgressClick: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val restWPx = with(density) { 18.dp.toPx() }
    val restHPx = with(density) { 11.dp.toPx() }
    val trackHPx = with(density) { 3.dp.toPx() }

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline

    val scope = rememberCoroutineScope()
    var interacting by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var physicsRun by remember { mutableStateOf(0) }
    val headScale = remember { Animatable(1f) }
    val visual = remember { Animatable(progress) }
    val anchor = remember { Animatable(progress) }

    // Glide the resting pill smoothly between coarse (500ms) playback updates.
    LaunchedEffect(progress) {
        if (!interacting) {
            visual.animateTo(progress, tween(durationMillis = 450, easing = LinearEasing))
        }
    }

    // Gooey physics: the lagging anchor chases the head via a manual damped
    // spring (semi-implicit Euler). Launched on each drag, runs through release
    // until it settles — no per-frame coroutine allocation.
    LaunchedEffect(physicsRun) {
        if (physicsRun == 0) return@LaunchedEffect
        val stiffness = 1400f
        val damping = 2f * 0.85f * sqrt(stiffness) // dampingRatio ≈ 0.85 — calmer, less lag
        var vel = 0f
        var lastFrame = withFrameNanos { it }
        while (interacting || abs(visual.value - anchor.value) > 0.0005f || abs(vel) > 0.0005f) {
            val now = withFrameNanos { it }
            val dt = ((now - lastFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            lastFrame = now
            val force = (visual.value - anchor.value) * stiffness - vel * damping
            vel += force * dt
            anchor.snapTo((anchor.value + vel * dt).coerceIn(0f, 1f))
        }
        anchor.snapTo(visual.value)
        settling = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(24.dp) // tall touch target
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    scope.launch { visual.snapTo(fraction); anchor.snapTo(fraction) }
                    onProgressClick(fraction)
                    // Quick acknowledging pulse.
                    scope.launch {
                        headScale.animateTo(
                            1.18f,
                            spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessHigh)
                        )
                        headScale.animateTo(
                            1f,
                            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
                        )
                    }
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        interacting = true
                        settling = true
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        scope.launch { visual.snapTo(fraction); anchor.snapTo(fraction) }
                        onProgressClick(fraction)
                        scope.launch {
                            headScale.animateTo(
                                1.15f,
                                spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
                            )
                        }
                        physicsRun++
                    },
                    onDragEnd = {
                        interacting = false
                        scope.launch {
                            headScale.animateTo(
                                1f,
                                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
                            )
                        }
                    },
                    onDragCancel = {
                        interacting = false
                        scope.launch {
                            headScale.animateTo(
                                1f,
                                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
                            )
                        }
                    }
                ) { change, _ ->
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    scope.launch { visual.snapTo(fraction) }
                    onProgressClick(fraction)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = size.width
            val centerY = size.height / 2f
            val half = restWPx / 2f

            // Base track.
            val trackTop = centerY - trackHPx / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, trackTop),
                size = Size(trackWidth, trackHPx),
                cornerRadius = CornerRadius(trackHPx / 2f)
            )
            // Filled portion up to the head.
            val vX = visual.value.coerceIn(0f, 1f) * trackWidth
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(0f, trackTop),
                size = Size(vX, trackHPx),
                cornerRadius = CornerRadius(trackHPx / 2f)
            )

            // Capsule playhead — spans [anchor, head] while gooey, clean pill otherwise.
            val headX = vX.coerceIn(half, (trackWidth - half).coerceAtLeast(half))
            val gooey = interacting || settling
            // Cap how far the pill is allowed to stretch so the effect stays subtle.
            val maxStretchPx = restWPx * 1.6f
            val tailX = if (gooey) {
                val raw = (anchor.value.coerceIn(0f, 1f) * trackWidth)
                    .coerceIn(half, (trackWidth - half).coerceAtLeast(half))
                headX + (raw - headX).coerceIn(-maxStretchPx, maxStretchPx)
            } else {
                headX
            }
            val gapPx = abs(headX - tailX)
            // Volume-preserving feel: the more it stretches, the thinner it gets — kept gentle.
            val squash = (1f / (1f + gapPx / (restWPx * 6f))).coerceIn(0.78f, 1f)
            val h = restHPx * headScale.value * squash
            val leftX = min(headX, tailX) - half
            val rightX = max(headX, tailX) + half
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(leftX, centerY - h / 2f),
                size = Size(rightX - leftX, h),
                cornerRadius = CornerRadius(h / 2f)
            )
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
