package com.mobixournal.ui

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.audio.AudioTranscription
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
    playbackSpeed: Float = 1.0f,
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

    val activeSubtitle = remember(playheadMs, transcription) {
        transcription?.segmentAt(playheadMs)?.text
    }

    val glassColor = rememberFloatingSurfaceColor()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            },
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = glassColor,
            shadowElevation = 8.dp,
            modifier = Modifier.width(360.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                MiniPlayerHeader(
                    playheadMs = playheadMs,
                    durationMs = durationMs,
                    playbackSpeed = playbackSpeed,
                    onSpeedChange = onSpeedChange,
                    onClose = onClose,
                )

                // Interactive Waveform & Heatmap
                Spacer(Modifier.height(8.dp))
                WaveformScrubber(
                    waveform = waveform,
                    heatmap = heatmap,
                    playheadMs = playheadMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                )

                // Subtitle preview line if available
                if (!activeSubtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "“$activeSubtitle”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
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
private fun MiniPlayerHeader(
    playheadMs: Int,
    durationMs: Int,
    playbackSpeed: Float,
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
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${timeFormat(playheadMs)} / ${timeFormat(durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        SpeedSelectorPill(
            currentSpeed = playbackSpeed,
            onSpeedChange = onSpeedChange,
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close player",
                modifier = Modifier.size(16.dp),
            )
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
        IconButton(onClick = { onSkip(-5000) }) {
            Icon(Icons.Outlined.FastRewind, contentDescription = "Rewind 5s")
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(
                imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { onSkip(5000) }) {
            Icon(Icons.Outlined.FastForward, contentDescription = "Forward 5s")
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
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val heatColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(trackColor.copy(alpha = 0.25f))
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
        Canvas(modifier = Modifier.fillMaxWidth().height(52.dp)) {
            val count = waveform.size.coerceAtLeast(1)
            val barW = size.width / count
            val playedFraction = if (durationMs > 0) (playheadMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            val playheadX = playedFraction * size.width

            for (i in waveform.indices) {
                val amp = waveform[i].coerceIn(0.1f, 1.0f)
                val barH = amp * (size.height - 10.dp.toPx())
                val x = i * barW
                val y = (size.height - barH) / 2f
                val isPlayed = x <= playheadX

                drawRoundRect(
                    color = if (isPlayed) primaryColor else trackColor,
                    topLeft = Offset(x + 1f, y),
                    size = Size((barW - 2f).coerceAtLeast(1f), barH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
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
                            topLeft = Offset(i * heatW, size.height - 3.dp.toPx()),
                            size = Size(heatW, 3.dp.toPx()),
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

        androidx.compose.material3.DropdownMenu(
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
