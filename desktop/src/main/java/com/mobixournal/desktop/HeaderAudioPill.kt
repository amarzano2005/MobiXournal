package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The window's **audio pill**: the tablet's capsule of three round buttons, sitting in the header
 * immediately **left of Save**.
 *
 * The shape is mobile's on purpose (`AudioMiniCapsule` is the reference): one glass capsule holding a
 * microphone/stop, a pause and a play, in that order, each a circle that fills with its own state colour
 * — red while the microphone is open, amber while it is paused mid-take, the accent while a clip sounds.
 * The window adds only what a mouse needs: a hover step, a tooltip-free time readout beside the buttons,
 * and a level bar under the take's time so a silent microphone is visible before the take is over.
 *
 * What the buttons **do** is [DesktopAudioSession]'s, not this file's: record/stop starts or finalises a
 * `.wav` in the store, pause mutes it, and play holds or resumes the recording the page's strokes name.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HeaderAudioPill(
    audio: DesktopAudioSession,
    /** The tab a recording belongs to, asked at the click — switching tabs mid-take keeps it honest. */
    tab: () -> DesktopTab?,
    modifier: Modifier = Modifier,
) {
    // The engines' own clocks are not Compose state: one poll per tick is cheaper than a state write per
    // sample, and 100 ms is finer than the readout can show.
    LaunchedEffect(Unit) {
        while (true) {
            audio.refresh()
            delay(100)
        }
    }
    val recording = audio.recording
    val paused = audio.recordingPaused
    val playing = audio.playing
    val clipOn = audio.clipName != null
    val time = when {
        recording -> formatTime(audio.recordedMs)
        clipOn -> "${formatTime(audio.positionMs)} / ${formatTime(audio.durationMs)}"
        else -> null
    }

    Row(
        modifier
            .height(DesktopTheme.headerControlSize)
            .glassCapsule(
                cornerRadius = DesktopTheme.headerControlSize / 2,
                fill = DesktopTheme.glass,
                shadow = DesktopTheme.bandShadow,
            )
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(
            description = if (recording) "Stop recording" else "Start audio recording",
            fill = if (recording) DesktopTheme.documentRed else Color.Transparent,
            tint = if (recording) Color.White else DesktopTheme.chromeInk,
            onClick = { audio.toggleRecord(tab()?.document?.model) },
        ) { colour, size ->
            if (recording) {
                drawRecordStop(colour, size)
            } else {
                drawMicrophone(colour, size)
            }
        }
        PillButton(
            description = if (paused) "Resume recording" else "Pause",
            enabled = recording || clipOn,
            fill = if (paused && recording) DesktopTheme.accent else if (recording && !paused) DesktopTheme.danger else Color.Transparent,
            tint = when {
                (recording && !paused) || (paused && recording) -> Color.White
                recording || clipOn -> DesktopTheme.chromeInk
                else -> DesktopTheme.inkMuted
            },
            onClick = {
                if (recording) audio.toggleRecordPause() else if (playing) audio.togglePlay(tab())
            },
        ) { colour, size ->
            // A **resume-recording** mark is the record dot, not the play triangle: a triangle here
            // would read as "start playback", which is the button beside it (`AudioMiniCapsule`'s own
            // reasoning, kept because the two shells must say the same thing the same way).
            if (paused && recording) drawRecordDot(colour, size) else drawPauseBars(colour, size)
        }
        PillButton(
            description = if (playing) "Pause playback" else "Play audio",
            enabled = clipOn || audio.hasAudio || recording,
            fill = if (playing) DesktopTheme.accent.copy(alpha = 0.25f) else Color.Transparent,
            tint = if (playing) DesktopTheme.accent else DesktopTheme.chromeInk,
            onClick = { audio.togglePlay(tab()) },
        ) { colour, size ->
            if (playing) drawPauseBars(colour, size) else drawPlayTriangle(colour, size)
        }
        if (clipOn) {
            Spacer(Modifier.width(2.dp))
            PillScrubber(audio)
        }
        if (recording || clipOn) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = time ?: "",
                color = if (recording) DesktopTheme.documentRed else DesktopTheme.inkSecondary,
                style = DesktopTheme.numeric,
            )
        }
        if (clipOn) {
            Spacer(Modifier.width(2.dp))
            SpeedButton(audio.speed, onClick = audio::cycleSpeed)
        }
        if (recording) {
            Spacer(Modifier.width(4.dp))
            LevelBars(audio.level)
            Spacer(Modifier.width(4.dp))
        }
    }
}

/**
 * The speed the clip plays at, as a word a person can click: `1×`, `1.25×`, `1.5×`, `2×`.
 *
 * A button rather than a menu: the transport is one capsule and the four steps are the ones a lecture is
 * listened to at, so a click that walks them is less chrome than a picker that has to be opened. It sits
 * beside the readout because they answer the same question — where am I, and how fast is it going.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SpeedButton(speed: Float, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) DesktopTheme.hoverSlot else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .pointerInput(speed) { detectTapGestures { onClick() } }
            .padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        Text(
            text = speedLabel(speed),
            color = if (speed == 1f) DesktopTheme.inkSecondary else DesktopTheme.accent,
            style = DesktopTheme.numeric,
        )
    }
}

/** `1×` for a whole number, `1.25×` for a step between: the label the button wears. */
internal fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}\u00d7" else "$speed\u00d7"

/**
 * The pill's **scrubber**: a hairline of the clip with the played part lit, which a press or a drag
 * walks.
 *
 * The tablet has no scrubber because a finger already knows where it wants to be on a recording it just
 * made; a mouse on a two-minute take needs one, and it belongs in this capsule rather than in a panel of
 * its own — the transport stays one object, left of Save. The time is [DesktopAudioSession.scrubTargetMs]
 * so a drag past either end settles on the clip's ends rather than on a negative or an overrun.
 */
@Composable
private fun PillScrubber(audio: DesktopAudioSession) {
    val duration = audio.durationMs
    val progress = if (duration > 0) (audio.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Canvas(
        Modifier
            .width(56.dp)
            .height(16.dp)
            .pointerInput(duration) {
                if (duration <= 0) return@pointerInput
                fun scrub(x: Float) {
                    audio.seek(audio.scrubTargetMs(x / size.width))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    scrub(down.position.x)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull { it.pressed }?.let { scrub(it.position.x) }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val trackY = this.size.height / 2f
        val trackHeight = 2.dp.toPx()
        drawRoundRect(
            color = DesktopTheme.activeSlot,
            topLeft = Offset(0f, trackY - trackHeight / 2f),
            size = Size(this.size.width, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
        )
        drawRoundRect(
            color = DesktopTheme.accent,
            topLeft = Offset(0f, trackY - trackHeight / 2f),
            size = Size(this.size.width * progress, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
        )
        // The head is what the pointer grabbed: without it a click on a still bar looks like nothing.
        drawCircle(
            color = DesktopTheme.accent,
            radius = 3.dp.toPx(),
            center = Offset(this.size.width * progress, trackY),
        )
    }
}

/**
 * One round button of the pill, at the tablet's 24 dp — the same target the phone's capsule draws, with
 * the window's hover step added (`docs/interface-contract.md` §6: a hover is not a pick).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PillButton(
    description: String,
    enabled: Boolean = true,
    fill: Color = Color.Transparent,
    tint: Color = DesktopTheme.chromeInk,
    onClick: () -> Unit,
    draw: DrawScope.(Color, Float) -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val background = when {
        fill != Color.Transparent -> fill
        hovered && enabled -> DesktopTheme.hoverSlot
        else -> Color.Transparent
    }
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(background)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .pointerInput(enabled, description) { if (enabled) detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            draw(if (enabled) tint else DesktopTheme.inkMuted, size.minDimension)
        }
    }
}

/** A thin level bar: the take's peak, so a muted or dead microphone is visible while it happens. */
@Composable
private fun LevelBars(level: Float) {
    Box(Modifier.width(22.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(DesktopTheme.activeSlot)) {
        Box(
            Modifier
                .width((22f * level.coerceIn(0f, 1f)).dp)
                .height(4.dp)
                .background(DesktopTheme.accent),
        )
    }
}

/** `mm:ss`, the readout the tablet's capsule shows. */
internal fun formatTime(ms: Int): String {
    val seconds = (ms.coerceAtLeast(0) / 1000)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

// --- the transport's own marks ---------------------------------------------------------------------
//
// Four small drawings, on the button's own 16 dp box: a microphone (body, cradle, stand), a stop square,
// a record dot, two pause bars and a play triangle. They are drawn here rather than added to the glyph
// catalogue because they are the **transport's** marks on two round buttons whose sizes are stated in
// one place, not tool faces the two toolbars must agree on.

private fun DrawScope.drawMicrophone(colour: Color, size: Float) {
    val bodyWidth = size * 0.34f
    val bodyTop = size * 0.08f
    val bodyBottom = size * 0.62f
    drawRoundRect(
        color = colour,
        topLeft = Offset((size - bodyWidth) / 2f, bodyTop),
        size = Size(bodyWidth, bodyBottom - bodyTop),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyWidth / 2f),
    )
    drawArc(
        color = colour,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size * 0.18f, size * 0.30f),
        size = Size(size * 0.64f, size * 0.56f),
        style = Stroke(width = size * 0.10f),
    )
    drawLine(
        colour,
        Offset(size / 2f, size * 0.86f),
        Offset(size / 2f, size * 0.98f),
        strokeWidth = size * 0.10f,
    )
}

private fun DrawScope.drawRecordDot(colour: Color, size: Float) {
    drawCircle(colour, radius = size * 0.28f, center = Offset(size / 2f, size / 2f))
}

private fun DrawScope.drawRecordStop(colour: Color, size: Float) {
    drawRoundRect(
        color = colour,
        topLeft = Offset(size * 0.24f, size * 0.24f),
        size = Size(size * 0.52f, size * 0.52f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * 0.10f),
    )
}

private fun DrawScope.drawPauseBars(colour: Color, size: Float) {
    val barWidth = size * 0.18f
    val top = size * 0.20f
    val height = size * 0.60f
    drawRoundRect(
        color = colour,
        topLeft = Offset(size * 0.26f, top),
        size = Size(barWidth, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
    )
    drawRoundRect(
        color = colour,
        topLeft = Offset(size * 0.56f, top),
        size = Size(barWidth, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
    )
}

private fun DrawScope.drawPlayTriangle(colour: Color, size: Float) {
    val path = Path().apply {
        moveTo(size * 0.32f, size * 0.16f)
        lineTo(size * 0.86f, size * 0.50f)
        lineTo(size * 0.32f, size * 0.84f)
        close()
    }
    drawPath(path, colour)
}
