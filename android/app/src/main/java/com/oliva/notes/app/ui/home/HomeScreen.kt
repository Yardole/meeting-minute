package com.oliva.notes.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.oliva.notes.app.ui.components.EdgeScrollHaptics
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oliva.notes.app.domain.model.Meeting
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    onMeetingClick: (String) -> Unit,
    onRecordClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val meetings by viewModel.filteredMeetings.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val expanded by viewModel.isFabExpanded.collectAsState()

    // Audio file import picker
    val context = androidx.compose.ui.platform.LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importAudio(it, context) }
    }

    Scaffold(
        floatingActionButton = {
            val fabHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mini FABs — slide out from behind the main FAB
                AnimatedVisibility(
                    visible = expanded,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(animationSpec = tween(150)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeOut(animationSpec = tween(150))
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Import button with label
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = expanded,
                                enter = fadeIn(animationSpec = tween(300, delayMillis = 200)),
                                exit = fadeOut(animationSpec = tween(100))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Import",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                    ) {
                                        viewModel.collapseFab()
                                        importLauncher.launch(arrayOf("audio/*"))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = "Import audio",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Record button with label — shared element + no ripple
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = expanded,
                                enter = fadeIn(animationSpec = tween(300, delayMillis = 250)),
                                exit = fadeOut(animationSpec = tween(100))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Record",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }
                            with(sharedTransitionScope) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .sharedBounds(
                                            rememberSharedContentState(key = "record-btn"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                        ) {
                                            onRecordClick()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Record meeting",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Main + FAB — morphs + ↔ × via rotation + tap bounce
                val fabRotation by animateFloatAsState(
                    targetValue = if (expanded) 45f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "fabRotate"
                )

                val fabScale = remember { Animatable(1f) }
                val scope = rememberCoroutineScope()

                FloatingActionButton(
                    onClick = {
                        val expanding = !expanded
                        viewModel.toggleFab()
                        if (expanding) {
                            scope.launch {
                                delay(50)
                                repeat(4) {
                                    fabHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    delay(100)
                                }
                            }
                        }
                        scope.launch {
                            fabScale.snapTo(0.88f)
                            fabScale.animateTo(
                                1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .scale(fabScale.value)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (expanded) "Close" else "New meeting",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(fabRotation)
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus() }
                }
        ) {
            Text(
                text = "Oliva",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            )

            // Search bar
            var isSearchFocused by remember { mutableStateOf(false) }
            val searchBarBg by animateColorAsState(
                targetValue = if (isSearchFocused)
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                animationSpec = tween(400)
            )
            val searchBarBorder by animateColorAsState(
                targetValue = if (isSearchFocused)
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                animationSpec = tween(400)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(searchBarBg)
                    .border(1.dp, searchBarBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (isSearchFocused) 0.7f else 0.4f
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .onFocusChanged { isSearchFocused = it.isFocused },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search meetings",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = if (isSearchFocused) 0.6f else 0.4f
                                            )
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.clearSearch() }
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            val listState = rememberLazyListState()
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            EdgeScrollHaptics(listState, haptic)

            val isRefreshing by viewModel.isRefreshing.collectAsState()
            val pullRefreshState = rememberPullRefreshState(
                refreshing = isRefreshing,
                onRefresh = { viewModel.refresh() }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                if (meetings.isEmpty()) {
                    // Empty state — different text for no meetings vs no search results
                    val isSearching = searchQuery.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp)
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isSearching) "No results" else "No meetings yet",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = com.oliva.notes.app.ui.theme.Fraunces,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            )
                            Text(
                                text = if (isSearching)
                                    "No meetings match \"$searchQuery\""
                                else
                                    "Tap + to record or import audio",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(meetings, key = { it.id }) { meeting ->
                            MeetingListItem(
                                meeting = meeting,
                                onClick = { onMeetingClick(meeting.id.toString()) },
                                onDelete = { viewModel.deleteMeeting(meeting.id) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    }
                }

                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        } // end Column

    } // end content Box
} // end Scaffold content
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun MeetingListItem(
    meeting: Meeting,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thresholdPx = with(density) { 80.dp.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    var deleted by remember { mutableStateOf(false) }
    var passedThreshold by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Red delete background — revealed as you swipe
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.error)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "Delete",
                color = MaterialTheme.colorScheme.onError,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier.alpha((-offsetX / thresholdPx).coerceIn(0f, 1f))
            )
        }

        // Foreground content — swipeable
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.toInt().coerceAtMost(0), 0) }
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            passedThreshold = false
                            if (-offsetX > thresholdPx) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                deleted = true
                                scope.launch {
                                    val anim = Animatable(offsetX)
                                    anim.animateTo(-2f * thresholdPx, tween(200))
                                    offsetX = anim.value
                                    onDelete()
                                }
                            } else {
                                // Spring back
                                scope.launch {
                                    val anim = Animatable(offsetX)
                                    anim.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                    offsetX = 0f
                                }
                            }
                        },
                        onHorizontalDrag = { _: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                            if (!deleted) {
                                val prevOffset = offsetX
                                offsetX = (offsetX + dragAmount).coerceIn(-thresholdPx * 2f, 0f)
                                // Haptic when crossing the threshold
                                if (!passedThreshold && -offsetX > thresholdPx) {
                                    passedThreshold = true
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                } else if (passedThreshold && -offsetX <= thresholdPx) {
                                    passedThreshold = false
                                }
                            }
                        }
                    )
                }
                .background(MaterialTheme.colorScheme.background)
        ) {
            with(sharedTransitionScope) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .sharedBounds(
                            rememberSharedContentState(key = "meeting-${meeting.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                        .clickable(onClick = onClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    MeetingContentInner(meeting = meeting)
                }
            }
        }
    }
}

@Composable
private fun MeetingContentInner(
    meeting: Meeting
) {
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val dateStr = java.time.Instant
        .ofEpochMilli(meeting.recordedAt.toEpochMilli())
        .atZone(java.time.ZoneId.systemDefault())
        .format(formatter)

    val durationMin = meeting.durationMs / 60000
    val durationStr = if (durationMin < 1) "${meeting.durationMs / 1000} sec" else "$durationMin min"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = meeting.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$dateStr · $durationStr",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }

        Box(
            modifier = Modifier
                .padding(start = 12.dp, top = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = meeting.status.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
