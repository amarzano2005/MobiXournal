package com.mobixournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TransformOrigin
import com.mobixournal.audio.AudioTranscription
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor
import kotlin.math.roundToInt

/**
 * Floating frosted-glass mini-player pill providing:
 * - Real audio amplitude waveform rendering with interactive drag/tap scrubber
 * - Stroke inking density heatmap band
 * - Play / Pause, +/- 5s skip buttons
 * - Playback speed selector (0.8x, 1.0x, 1.25x, 1.5x, 2.0x)
 * - Live synchronized subtitle preview from [transcription]
 */
@Composable
fun AudioMiniPlayer(
    visible: Boolean,
    playing: Boolean,
    playheadMs: Int,
    durationMs: Int,
    waveform: List<Float>,
    heatmap: List<Float> = emptyList(),
    transcription: AudioTranscription? = null,
    clipTitle: String? = null,
    playbackSpeed: Float = 1.0f,
    isTranscribing: Boolean = false,
    transcriptionStatus: String? = null,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onSkip: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showTranscript by remember { mutableStateOf(false) }

    val activeSubtitle = remember(playheadMs, transcription) {
        transcription?.segmentAt(playheadMs)?.text
    }

    val glassColor = rememberFloatingSurfaceColor()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) },
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = glassColor,
            shadowElevation = 8.dp,
            modifier = Modifier
                .width(300.dp)
                .floatingRim(16.dp)
                .liquidGlassSpringAnimation(expanded = visible, origin = TransformOrigin(0.5f, 0.5f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlayerDragHandle { dx, dy ->
                    offsetX += dx
                    offsetY += dy
                }

                MiniPlayerHeader(
                    playheadMs = playheadMs,
                    durationMs = durationMs,
                    clipTitle = clipTitle,
                    playbackSpeed = playbackSpeed,
                    showTranscript = showTranscript,
                    onToggleTranscript = { showTranscript = !showTranscript },
                    onSpeedChange = onSpeedChange,
                    onClose = onClose,
                )

                // Interactive Waveform & Heatmap
                Spacer(Modifier.height(6.dp))
                WaveformScrubber(
                    waveform = waveform,
                    heatmap = heatmap,
                    playheadMs = playheadMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                )

                // Subtitle preview line or interactive Transcript panel
                SubtitleOrTranscriptSection(
                    showTranscript = showTranscript,
                    transcription = transcription,
                    activeSubtitle = activeSubtitle,
                    playheadMs = playheadMs,
                    isTranscribing = isTranscribing,
                    transcriptionStatus = transcriptionStatus,
                    onSeek = onSeek,
                    onOpenTranscript = { showTranscript = true },
                )

                Spacer(Modifier.height(6.dp))
                MiniPlayerControls(
                    playing = playing,
                    onTogglePlay = onTogglePlay,
                    onSkip = onSkip,
                )
            }
        }
    }
}

@Composable
private fun PlayerDragHandle(onDrag: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 2.dp, bottom = 4.dp)
            .width(32.dp)
            .height(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
    )
}

@Composable
private fun MiniPlayerHeader(
    playheadMs: Int,
    durationMs: Int,
    clipTitle: String?,
    playbackSpeed: Float,
    showTranscript: Boolean,
    onToggleTranscript: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${timeFormat(playheadMs)} / ${timeFormat(durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!clipTitle.isNullOrBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = clipTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleTranscript,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Subtitles,
                    contentDescription = "Toggle transcript panel",
                    tint = if (showTranscript) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            SpeedSelectorPill(
                currentSpeed = playbackSpeed,
                onSpeedChange = onSpeedChange,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close player",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SubtitleOrTranscriptSection(
    showTranscript: Boolean,
    transcription: AudioTranscription?,
    activeSubtitle: String?,
    playheadMs: Int,
    isTranscribing: Boolean = false,
    transcriptionStatus: String? = null,
    onSeek: (Int) -> Unit,
    onOpenTranscript: () -> Unit,
) {
    if (showTranscript) {
        Spacer(Modifier.height(4.dp))
        TranscriptPanel(
            transcription = transcription,
            playheadMs = playheadMs,
            onSeek = onSeek,
        )
    } else {
        Spacer(Modifier.height(4.dp))
        val displayText = when {
            isTranscribing -> "Transcribing..."
            !activeSubtitle.isNullOrBlank() -> "“$activeSubtitle”"
            transcription != null && transcription.segments.isNotEmpty() -> "Transcript (${transcription.segments.size} segments)"
            !transcriptionStatus.isNullOrBlank() -> transcriptionStatus
            else -> "Transcript (tap to open)"
        }
        val isHighlighted = !activeSubtitle.isNullOrBlank() || isTranscribing

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                )
                .clickable(enabled = !isTranscribing, onClick = onOpenTranscript)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isTranscribing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Subtitles,
                    contentDescription = null,
                    tint = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TranscriptPanel(
    transcription: AudioTranscription?,
    playheadMs: Int,
    onSeek: (Int) -> Unit,
) {
    val segments = transcription?.segments.orEmpty()
    if (segments.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No transcript available",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(4.dp),
    ) {
        items(segments.size) { index ->
            val segment = segments[index]
            val isActive = playheadMs in segment.startTimeMs..segment.endTimeMs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else Color.Transparent,
                    )
                    .clickable { onSeek(segment.startTimeMs) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timeFormat(segment.startTimeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = segment.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerControls(
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onSkip: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable { onSkip(-5000) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.FastRewind,
                contentDescription = "Rewind 5s",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable { onSkip(5000) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.FastForward,
                contentDescription = "Forward 5s",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun WaveformScrubber(
    waveform: List<Float>,
    heatmap: List<Float>,
    playheadMs: Int,
    durationMs: Int,
    onSeek: (Int) -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val unplayedBarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val heatColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(trackBgColor)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((fraction * durationMs).toInt())
                }
            }
            .pointerInput(durationMs) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek((fraction * durationMs).toInt())
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            val count = waveform.size.coerceAtLeast(1)
            val barW = size.width / count
            val playedFraction = if (durationMs > 0) (playheadMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            val playheadX = playedFraction * size.width

            for (i in waveform.indices) {
                val amp = waveform[i].coerceIn(0.12f, 1.0f)
                val barH = amp * (size.height - 6.dp.toPx())
                val x = i * barW
                val y = (size.height - barH) / 2f
                val isPlayed = x <= playheadX

                drawRoundRect(
                    color = if (isPlayed) primaryColor else unplayedBarColor,
                    topLeft = Offset(x + 1f, y),
                    size = Size((barW - 2f).coerceAtLeast(1f), barH),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                )
            }

            // Draw writing density heatmap indicator along the bottom edge
            if (heatmap.isNotEmpty()) {
                val heatCount = heatmap.size
                val heatW = size.width / heatCount
                for (i in heatmap.indices) {
                    val density = heatmap[i].coerceIn(0f, 1f)
                    if (density > 0f) {
                        drawRect(
                            color = heatColor.copy(alpha = density),
                            topLeft = Offset(i * heatW, size.height - 2.5.dp.toPx()),
                            size = Size(heatW, 2.5.dp.toPx()),
                        )
                    }
                }
            }

            // Draw glowing playhead cursor line
            drawLine(
                color = primaryColor,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

@Composable
private fun SpeedSelectorPill(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    val speeds = listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f)
    var expanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = "${currentSpeed}×",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        ToolbarMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            speeds.forEach { speed ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("${speed}×") },
                    onClick = {
                        expanded = false
                        onSpeedChange(speed)
                    },
                )
            }
        }
    }
}

private fun timeFormat(ms: Int): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
