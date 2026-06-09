package com.oliva.notes.app.ui.meetingdetail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oliva.notes.app.data.local.entity.ChatRole
import com.oliva.notes.app.domain.model.Meeting
import com.oliva.notes.app.domain.model.Speaker
import com.oliva.notes.app.domain.model.TranscriptSegment
import com.oliva.notes.app.ui.components.BottomPlayer
import com.oliva.notes.app.ui.components.EdgeScrollHaptics
import com.oliva.notes.app.ui.components.ShareSheet
import java.util.UUID

@Composable
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
fun MeetingDetailScreen(
    meetingId: String,
    onBackClick: () -> Unit,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    viewModel: MeetingDetailViewModel = hiltViewModel()
) {
    val meeting by viewModel.meeting.collectAsState()
    val segments by viewModel.transcriptSegments.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val speakers by viewModel.speakers.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val chatInput by viewModel.chatInput.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val chatError by viewModel.chatError.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Minutes", "Transcript", "Chat")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Speaker rename dialog state
    var renameSpeakerId by remember { mutableStateOf<UUID?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Share sheet state
    var showShareSheet by remember { mutableStateOf(false) }

    LaunchedEffect(meeting?.localAudioPath) {
        meeting?.localAudioPath?.let { path ->
            viewModel.prepareAudio(path)
        }
    }

    // Speaker rename dialog
    if (renameSpeakerId != null) {
        val speaker = speakers.find { it.id == renameSpeakerId }
        AlertDialog(
            onDismissRequest = { renameSpeakerId = null },
            title = { Text("Rename Speaker") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Speaker name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameSpeakerId?.let { viewModel.renameSpeaker(it, renameText) }
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    renameSpeakerId = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameSpeakerId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            val isLandscape =
                androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE

            // Header — shared element from meeting card, grouped in a Column
            with(sharedTransitionScope) {
                Column(
                    modifier = Modifier
                        .sharedBounds(
                            rememberSharedContentState(key = "meeting-$meetingId"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = if (isLandscape) 6.dp else 8.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onBackClick()
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "← Back",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 13.sp
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            showShareSheet = true
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Share",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            // Editable title
            var isEditing by remember { mutableStateOf(false) }
            var editText by remember { mutableStateOf("") }
            val titleFontSize = if (isLandscape) 18.sp else 22.sp
            val titleVPadding = if (isLandscape) 1.dp else 2.dp

            if (isEditing) {
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = com.oliva.notes.app.ui.theme.Fraunces,
                        fontSize = titleFontSize,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (editText.isNotBlank()) {
                                viewModel.updateTitle(editText)
                            }
                            isEditing = false
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = titleVPadding)
                        .focusRequester(focusRequester)
                )
            } else {
                Text(
                    text = meeting?.title ?: "Meeting",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = com.oliva.notes.app.ui.theme.Fraunces,
                        fontSize = titleFontSize
                    ),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = titleVPadding)
                        .clickable {
                            editText = meeting?.title ?: ""
                            isEditing = true
                        }
                )
            }

            // Date + duration subtitle
            meeting?.let { m ->
                val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.getDefault())
                val dateStr = java.time.Instant
                    .ofEpochMilli(m.recordedAt.toEpochMilli())
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(formatter)
                val durationMin = m.durationMs / 60000
                val durationStr = if (durationMin < 1) "${m.durationMs / 1000} sec" else "$durationMin min"
                Text(
                    text = "$dateStr · $durationStr",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
                } // end Column (sharedBounds)
            } // end with(sharedTransitionScope)

            Spacer(modifier = Modifier.height(12.dp))

            if (isLandscape) {
                // Landscape: player on left, tabs + content on right
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left: player — vertically centered, fills height
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        BottomPlayer(
                            modifier = Modifier.fillMaxWidth(),
                            currentPositionMs = currentPosition,
                            durationMs = duration,
                            isPlaying = isPlaying,
                            onPlayPause = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); viewModel.togglePlayback() },
                            onSeekBack = { viewModel.seekTo((currentPosition - 10000).coerceAtLeast(0)) },
                            onSeekForward = { viewModel.seekTo((currentPosition + 10000).coerceAtMost(duration)) },
                            speed = speed,
                            onSpeedChange = { viewModel.cycleSpeed() },
                            onProgressClick = { fraction ->
                                viewModel.seekTo((fraction * duration).toInt())
                            }
                        )
                    }

                    // Right: tabs + content
                    Column(modifier = Modifier.weight(0.6f)) {
                        AnimatedTabRow(
                            selectedIndex = selectedTab,
                            tabs = tabs,
                            onTabClick = { selectedTab = it }
                        )

                        when (selectedTab) {
                            0 -> MinutesTab(meeting, summary, onRetry = { viewModel.retryProcessing() })
                            1 -> TranscriptTab(
                                segments = segments,
                                speakers = speakers,
                                onTimeClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); viewModel.seekTo(it) },
                                onRenameSpeaker = { speakerId ->
                                    val spk = speakers.find { it.id == speakerId }
                                    renameText = spk?.name ?: spk?.label ?: ""
                                    renameSpeakerId = speakerId
                                }
                            )
                            2 -> ChatTab(
                                messages = chatMessages,
                                inputText = chatInput,
                                isSending = isSending,
                                onInputChanged = { viewModel.onChatInputChanged(it) },
                                onSend = { viewModel.sendMessage() }
                            )
                        }
                    }
                }
            } else {
                // Portrait: vertical stack
                BottomPlayer(
                    currentPositionMs = currentPosition,
                    durationMs = duration,
                    isPlaying = isPlaying,
                    onPlayPause = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); viewModel.togglePlayback() },
                    onSeekBack = { viewModel.seekTo((currentPosition - 10000).coerceAtLeast(0)) },
                    onSeekForward = { viewModel.seekTo((currentPosition + 10000).coerceAtMost(duration)) },
                    speed = speed,
                    onSpeedChange = { viewModel.cycleSpeed() },
                onProgressClick = { fraction ->
                    viewModel.seekTo((fraction * duration).toInt())
                }
                )

                AnimatedTabRow(
                    selectedIndex = selectedTab,
                    tabs = tabs,
                    onTabClick = { selectedTab = it }
                )

                when (selectedTab) {
                    0 -> MinutesTab(meeting, summary)
                    1 -> TranscriptTab(
                        segments = segments,
                        speakers = speakers,
                        onTimeClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); viewModel.seekTo(it) },
                        onRenameSpeaker = { speakerId ->
                            val spk = speakers.find { it.id == speakerId }
                            renameText = spk?.name ?: spk?.label ?: ""
                            renameSpeakerId = speakerId
                        }
                    )
                    2 -> ChatTab(
                        messages = chatMessages,
                        inputText = chatInput,
                        isSending = isSending,
                        chatError = chatError,
                        onDismissError = { viewModel.dismissChatError() },
                        onInputChanged = { viewModel.onChatInputChanged(it) },
                        onSend = { viewModel.sendMessage() }
                    )
                }
            }
        }
    }

    // Share bottom sheet
    ShareSheet(
        show = showShareSheet,
        onDismiss = { showShareSheet = false },
        meeting = meeting,
        summary = summary,
        segments = segments
    )
}

@Composable
private fun MinutesTab(
    meeting: Meeting?,
    summary: String?,
    onRetry: () -> Unit = {}
) {
    if (summary != null) {
        val listState = rememberLazyListState()
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        EdgeScrollHaptics(listState, haptic)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = com.oliva.notes.app.ui.theme.Fraunces,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            val status = meeting?.status?.name ?: ""
            Text(
                text = when {
                    status.startsWith("SUMMARIZING") -> "Generating summary"
                    status == "TRANSCRIBED" || status.startsWith("TRANSCRIBI") -> "Summary pending"
                    status == "ERROR" -> "An error occurred"
                    else -> "No summary yet"
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = com.oliva.notes.app.ui.theme.Fraunces,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                ),
                modifier = Modifier.padding(top = 32.dp)
            )
            Text(
                text = when {
                    status.startsWith("SUMMARIZING") -> "Oliva is generating a summary of this meeting."
                    status == "TRANSCRIBED" || status.startsWith("TRANSCRIBI") -> "Transcript is ready. Summary will be generated next."
                    status == "ERROR" -> "An error occurred during processing."
                    else -> "Record and upload audio to generate a summary."
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
            if (status == "ERROR") {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
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
            }
        }
    }
}

@Composable
private fun TranscriptTab(
    segments: List<TranscriptSegment>,
    speakers: List<Speaker>,
    onTimeClick: (Int) -> Unit,
    onRenameSpeaker: (UUID) -> Unit
) {
    if (segments.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "No transcript yet",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = com.oliva.notes.app.ui.theme.Fraunces,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                ),
                modifier = Modifier.padding(top = 32.dp)
            )
            Text(
                text = "Transcript will appear here once the audio has been processed.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    EdgeScrollHaptics(listState, haptic)
    LazyColumn(
        state = listState,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            segment.speakerId?.let { onRenameSpeaker(it) }
                        }
                    ) {
                        Text(
                            text = segment.speakerName,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = com.oliva.notes.app.ui.theme.Fraunces,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                        Text(
                            text = " ✎",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }
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

@Composable
private fun ChatTab(
    messages: List<Pair<ChatRole, String>>,
    inputText: String,
    isSending: Boolean,
    chatError: Boolean = false,
    onDismissError: () -> Unit = {},
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

        // Message area — scrolls fully behind the input bar
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Ask Oliva",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = com.oliva.notes.app.ui.theme.Fraunces,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    )
                    Text(
                        text = "Ask questions about this meeting.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            EdgeScrollHaptics(listState, haptic)

            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    delay(50)
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    if (lastVisible >= messages.size - 3 || lastVisible == -1) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages.size) { index ->
                    val (role, content) = messages[index]
                    val isUser = role == ChatRole.USER
                    ChatBubble(
                        message = content,
                        isUser = isUser,
                        roleLabel = if (isUser) "You" else "Oliva"
                    )
                }
            }
        }

        // Chat error indicator
        if (chatError) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 60.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Failed to send. Tap to retry.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = com.oliva.notes.app.ui.theme.WarmError
                    ),
                    modifier = Modifier.clickable { onSend(); onDismissError() }
                )
            }
        }

        // Input bar — floats on top of the scrolling content
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .offset(y = 4.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Ask about this meeting...",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isBlank() || isSending) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.primary
                            )
                            .clickable(enabled = inputText.isNotBlank() && !isSending) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSend()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSending) "..." else "→",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (inputText.isBlank() || isSending)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onPrimary,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: String,
    isUser: Boolean,
    roleLabel: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = roleLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = com.oliva.notes.app.ui.theme.Fraunces,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        if (isUser) {
            // User: olive bubble with tail bottom-right
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = 16.dp, bottomEnd = 4.dp
                    ))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp, lineHeight = 20.sp
                    )
                )
            }
        } else {
            // Oliva: no bubble, just text on the page
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp, lineHeight = 20.sp
                ),
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
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

@Composable
private fun AnimatedTabRow(
    selectedIndex: Int,
    tabs: List<String>,
    onTabClick: (Int) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tabPositions = remember { mutableStateListOf<androidx.compose.ui.geometry.Rect>() }

    // Ensure position list matches tabs
    if (tabPositions.size != tabs.size) {
        tabPositions.clear()
        repeat(tabs.size) { tabPositions.add(androidx.compose.ui.geometry.Rect.Zero) }
    }

    val targetRect = tabPositions.getOrNull(selectedIndex) ?: androidx.compose.ui.geometry.Rect.Zero
    val animOffsetX by animateFloatAsState(
        targetValue = targetRect.left,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "tabOffset"
    )
    val animWidth by animateFloatAsState(
        targetValue = targetRect.width,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "tabWidth"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Animated pill indicator
        Box(
            modifier = Modifier
                .offset { IntOffset(animOffsetX.toInt(), 0) }
                .size(
                    width = with(density) { animWidth.toDp() },
                    height = 32.dp
                )
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            tabs.forEachIndexed { index, title ->
                val isActive = index == selectedIndex
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInParent()
                            val size = coords.size
                            if (index < tabPositions.size) {
                                tabPositions[index] = androidx.compose.ui.geometry.Rect(
                                    pos.x, pos.y, pos.x + size.width, pos.y + size.height
                                )
                            }
                        }
                        .pointerInput(index) {
                            detectTapGestures {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onTabClick(index)
                            }
                        }
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
    }
}
