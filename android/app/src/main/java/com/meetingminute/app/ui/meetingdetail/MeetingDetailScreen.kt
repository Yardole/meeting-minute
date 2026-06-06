package com.meetingminute.app.ui.meetingdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meetingminute.app.domain.model.Meeting
import com.meetingminute.app.domain.model.TranscriptSegment
import com.meetingminute.app.ui.components.BottomPlayer

@Composable
fun MeetingDetailScreen(
    meetingId: String,
    onBackClick: () -> Unit,
    viewModel: MeetingDetailViewModel = hiltViewModel()
) {
    val meeting by viewModel.meeting.collectAsState()
    val segments by viewModel.transcriptSegments.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val speed by viewModel.speed.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Minutes", "Transcript", "Chat")

    LaunchedEffect(meeting?.localAudioPath) {
        meeting?.localAudioPath?.let { path ->
            viewModel.prepareAudio(path)
        }
    }

    Scaffold(
        bottomBar = {
            BottomPlayer(
                modifier = Modifier.navigationBarsPadding(),
                currentPositionMs = currentPosition,
                durationMs = duration,
                isPlaying = isPlaying,
                onPlayPause = { viewModel.togglePlayback() },
                onSeekBack = { viewModel.seekTo((currentPosition - 10000).coerceAtLeast(0)) },
                onSeekForward = { viewModel.seekTo((currentPosition + 10000).coerceAtMost(duration)) },
                speed = speed,
                onSpeedChange = { viewModel.cycleSpeed() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "< Back",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.clickable(onClick = onBackClick)
                )
                Text(
                    text = "Share",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                )
            }

            Text(
                text = meeting?.title ?: "Meeting",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 22.sp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isActive = index == selectedTab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.background
                            )
                            .clickable { selectedTab = index }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> MinutesTab(meeting)
                1 -> TranscriptTab(segments, onTimeClick = { positionMs ->
                    viewModel.seekTo(positionMs)
                })
                2 -> ChatTab()
            }
        }
    }
}

@Composable
private fun MinutesTab(meeting: Meeting?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val status = meeting?.status?.name ?: ""
        if (status.startsWith("TRANSCRIBI") || status == "SUMMARIZING" || status == "SUMMARIZED") {
            Text(
                text = "Summary will appear here once generated.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(top = 24.dp)
            )
        } else if (status == "TRANSCRIBED") {
            Text(
                text = "Transcript ready. Summary pending.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            Text(
                text = "Record and upload audio to get started.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

@Composable
private fun TranscriptTab(
    segments: List<TranscriptSegment>,
    onTimeClick: (Int) -> Unit
) {
    if (segments.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Transcript will appear here once processed.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(top = 24.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        itemsIndexed(segments) { _, segment ->
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = segment.speakerName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = formatTime(segment.startTimeMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.clickable { onTimeClick(segment.startTimeMs) }
                    )
                }
                Text(
                    text = segment.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun ChatTab() {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Chat will appear here once generated.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}
