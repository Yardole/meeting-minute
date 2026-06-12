package com.oliva.notes.app.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.RoundedCorner
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import com.oliva.notes.app.domain.model.Meeting
import com.oliva.notes.app.domain.model.TranscriptSegment
import com.oliva.notes.app.ui.theme.Fraunces
import com.oliva.notes.app.ui.theme.WarmOlive
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    meeting: Meeting?,
    summary: String?,
    segments: List<TranscriptSegment>
) {
    if (!show) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Match the device display's top corner radius
    val displayCornerRadius = remember {
        val density = context.resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val display = context.display
            val topLeftRadius = display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
            val topRightRadius = display?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0
            (maxOf(topLeftRadius, topRightRadius) / density).toInt().coerceIn(20, 48).dp
        } else {
            20.dp
        }
    }

    var includeSummary by remember { mutableStateOf(summary != null) }
    var includeTranscript by remember { mutableStateOf(segments.isNotEmpty()) }
    var selectedFormat by remember { mutableStateOf(ShareFormat.PLAIN_TEXT) }

    val hasSummary = !summary.isNullOrBlank()
    val hasTranscript = segments.isNotEmpty()
    val canShare = (includeSummary && hasSummary) || (includeTranscript && hasTranscript)

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMMM d, yyyy").withZone(ZoneId.systemDefault()) }
    val meetingTitle = meeting?.title ?: "Meeting"
    val meetingDate = meeting?.recordedAt?.let { dateFormatter.format(it) } ?: ""
    val durationMs = meeting?.durationMs ?: 0

    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = displayCornerRadius, topEnd = displayCornerRadius),
            dragHandle = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 32.dp)
            ) {
                // Title
                Text(
                    text = "Share",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // ── Content Selection ──────────────────────────────────────────

                Text(
                    text = "Content",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    ToggleRow(
                        label = "Summary",
                        subtitle = if (hasSummary) "AI-generated summary" else "Not available",
                        checked = includeSummary,
                        enabled = hasSummary,
                        onToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            includeSummary = !includeSummary
                        }
                    )
                    if (hasSummary && hasTranscript) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    ToggleRow(
                        label = "Transcript",
                        subtitle = if (hasTranscript) "${segments.size} segments" else "Not available",
                        checked = includeTranscript,
                        enabled = hasTranscript,
                        onToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            includeTranscript = !includeTranscript
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Format Selection ────────────────────────────────────────────

                Text(
                    text = "Format",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FormatChipRow(
                    selected = selectedFormat,
                    onSelect = { format ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedFormat = format
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Preview ─────────────────────────────────────────────────────

                val previewText = buildShareContent(
                    meetingTitle = meetingTitle,
                    meetingDate = meetingDate,
                    durationMs = durationMs,
                    summary = summary,
                    segments = segments,
                    includeSummary = includeSummary,
                    includeTranscript = includeTranscript,
                    format = selectedFormat
                )

                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val previewNestedScroll = remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset = available // eat leftover scroll, don't propagate to sheet
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                        .padding(12.dp)
                        .nestedScroll(previewNestedScroll)
                ) {
                    Text(
                        text = previewText.take(800).let {
                            if (it.length >= 800) "$it…" else it
                        }.ifEmpty { "Select content above to preview." },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Share Button ─────────────────────────────────────────────────

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        shareContent(
                            context = context,
                            title = meetingTitle,
                            content = previewText,
                            format = selectedFormat,
                            date = meetingDate
                        )
                        onDismiss()
                    },
                    enabled = canShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmOlive,
                        disabledContainerColor = WarmOlive.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = "Share Meeting",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ── Toggle Row ─────────────────────────────────────────────────────────────

@Composable
private fun ToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onToggle() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        checked && enabled -> WarmOlive
                        enabled -> MaterialTheme.colorScheme.outlineVariant
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

// ── Format Chip Row ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatChipRow(
    selected: ShareFormat,
    onSelect: (ShareFormat) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShareFormat.entries.forEach { format ->
            val isSelected = format == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) WarmOlive
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .clickable { onSelect(format) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = format.displayName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

// ── Share Intent ─────────────────────────────────────────────────────────────

private fun shareContent(
    context: Context,
    title: String,
    content: String,
    format: ShareFormat,
    date: String
) {
    try {
        when (format) {
            ShareFormat.PLAIN_TEXT, ShareFormat.MARKDOWN -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = format.mimeType()
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                context.startActivity(Intent.createChooser(intent, "Share $title"))
            }
            ShareFormat.PDF -> {
                val file = generatePdfFile(context, title, content)
                shareFile(context, file, format.mimeType(), title)
            }
            ShareFormat.WORD -> {
                val file = generateDocxFile(context, title, content)
                shareFile(context, file, format.mimeType(), title)
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share $title"))
}
