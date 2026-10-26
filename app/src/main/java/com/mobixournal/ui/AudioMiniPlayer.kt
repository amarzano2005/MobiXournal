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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TransformOrigin
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
 */
@Composable
fun AudioMiniPlayer(
    visible: Boolean,
    playing: Boolean,
    playheadMs: Int,
    durationMs: Int,
    waveform: List<Float>,
    heatmap: List<Float> = emptyList(),
    clipTitle: String? = null,
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
            MiniPlayerBody(
                playheadMs = playheadMs,
                durationMs = durationMs,
                waveform = waveform,
                heatmap = heatmap,
                clipTitle = clipTitle,
                playbackSpeed = playbackSpeed,
                playing = playing,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
                onSkip = onSkip,
                onSpeedChange = onSpeedChange,
                onClose = onClose,
                onDrag = { dx, dy ->
                    offsetX += dx
                    offsetY += dy
                },
            )
        }
    }
}

@Composable
private fun MiniPlayerBody(
    playheadMs: Int,
    durationMs: Int,
    waveform: List<Float>,
    heatmap: List<Float>,
    clipTitle: String?,
    playbackSpeed: Float,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onSkip: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlayerDragHandle(onDrag)

        MiniPlayerHeader(
            playheadMs = playheadMs,
            durationMs = durationMs,
            clipTitle = clipTitle,
            playbackSpeed = playbackSpeed,
            onSpeedChange = onSpeedChange,
            onClose = onClose,
        )

        Spacer(Modifier.height(6.dp))
        WaveformScrubber(
            waveform = waveform,
            heatmap = heatmap,
            playheadMs = playheadMs,
            durationMs = durationMs,
            onSeek = onSeek,
        )

        Spacer(Modifier.height(6.dp))
        MiniPlayerControls(
            playing = playing,
            onTogglePlay = onTogglePlay,
            onSkip = onSkip,
        )
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
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // The title half yields, the controls never do: the pill is a fixed 300 dp, so an uncapped
        // document name used to grow this Row until the speed selector and close button were pushed
        // out of the surface. The weight measures the controls at their own size first and gives the
        // title whatever is left, where it truncates instead.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
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
