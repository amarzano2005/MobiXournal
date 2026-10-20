package com.mobixournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The zoom slot's percentage label at full size; it is typeset at this times the rail's scale. */
private val ZOOM_LABEL = 14.sp

/** Breathing room inside the slot's circle, so the label never touches its edge. */
private val ZOOM_LABEL_INSET = 7.dp

/** How far the label may shrink to fit the slot — enough for "100%", never a speck. */
internal const val ZOOM_LABEL_MIN_SCALE = 0.62f

/**
 * The font size to draw a label at so it fits [availablePx], never larger than [baseSp] and never
 * smaller than [baseSp] × [ZOOM_LABEL_MIN_SCALE].
 *
 * The zoom slot is a fixed circle — the rail's adaptive pitch decides how big — and the percentage it
 * carries is the one thing in the rail whose text grows with the value: `41%` fits any slot, `100%`
 * and `381%` do not. Shrinking to fit keeps all three whole, instead of cropping the digits the user
 * is looking at. A label that was never measured (a non-positive width) is left at its base size.
 */
internal fun fittedLabelSp(baseSp: Float, measuredWidthPx: Float, availablePx: Float): Float {
    if (measuredWidthPx <= 0f || availablePx <= 0f || measuredWidthPx <= availablePx) return baseSp
    return (baseSp * (availablePx / measuredWidthPx)).coerceAtLeast(baseSp * ZOOM_LABEL_MIN_SCALE)
}

/**
 * The zoom level popup: shows the current zoom percentage with −/+/reset controls. The label is the
 * only slot face whose content is more than a glyph, so it is the one that reads the rail's scale
 * directly ([LocalRailSlotScale]) and the one that **fits itself to the slot** ([fittedLabelSp]): a
 * "100%" drawn at full size would be cropped by a shrunken slot, and a cropped percentage is worse
 * than a small one.
 *
 * @param zoom Current zoom factor (1.0 = 100%).
 * @param onZoomIn Zoom in by one step.
 * @param onZoomOut Zoom out by one step.
 * @param onZoomReset Reset to fit-to-width (100%).
 */
@Composable
internal fun ZoomPopupButton(zoom: Float, onZoomIn: () -> Unit, onZoomOut: () -> Unit, onZoomReset: () -> Unit) {
    ToolbarPopupButton(
        face = { open ->
            // A plain clickable box rather than a TextButton: a Button enforces Material's 48dp minimum
            // touch target (and its own 40dp minimum height) whatever its modifier asks for, which
            // would put the slot straight back to the size the rail's adaptive pitch shrank it out of.
            val slot = LocalRailSlotSize.current
            val label = zoomLabel(zoom)
            // Bold like the rest of the rail's faces: every other slot is a stroked glyph, and a
            // hairline percentage beside them read as a lighter tool rather than a value. The weight
            // rides on the style rather than on the Text alone, so the measurement `fittedLabelSp`
            // shrinks against is the width the bold glyphs really take.
            val textStyle = LocalTextStyle.current.copy(
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Bold,
            )
            val measurer = rememberTextMeasurer()
            val baseSp = ZOOM_LABEL.value * LocalRailSlotScale.current
            val measured = measurer.measure(
                text = AnnotatedString(label),
                style = textStyle.copy(fontSize = baseSp.sp),
                maxLines = 1,
                softWrap = false,
            )
            val availablePx = with(LocalDensity.current) { (slot - ZOOM_LABEL_INSET).toPx() }
            Box(
                modifier = Modifier
                    .size(slot)
                    .clip(CircleShape)
                    .clickable(onClick = open)
                    .semantics { contentDescription = "Zoom" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    softWrap = false,
                    style = textStyle,
                    fontSize = fittedLabelSp(baseSp, measured.size.width.toFloat(), availablePx).sp,
                )
            }
        },
    ) { dismiss ->
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onZoomOut) { Icon(Icons.Outlined.ZoomOut, contentDescription = "Zoom out") }
            TextButton(onClick = onZoomReset) {
                Text(text = zoomLabel(zoom), style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
            }
            IconButton(onClick = onZoomIn) { Icon(Icons.Outlined.ZoomIn, contentDescription = "Zoom in") }
        }
    }
}

/**
 * Everything the audio capsule needs ([AudioMiniCapsule]), bundled so its parameter list stays
 * readable. The recording controls live on that capsule, not on the rail: audio is not a rail slot.
 *
 * @property recording Whether the microphone is currently recording.
 * @property playing Whether audio playback is in progress.
 * @property folderChosen Whether the user has nominated a folder for audio sidecars.
 * @property saveStandalone Whether standalone audio export is enabled.
 * @property onToggleRecord Callback to start or stop recording.
 * @property onStopPlayback Callback to stop playback.
 * @property onChooseFolder Callback to open the folder picker.
 * @property onToggleSaveStandalone Callback to toggle standalone audio export.
 */
data class AudioUiState(
    /** Whether the microphone is currently recording. */
    val recording: Boolean = false,
    /** Whether audio playback is in progress. */
    val playing: Boolean = false,
    /** Whether a clip is on at all — sounding, or held at a position ([playheadMs]). */
    val clipOn: Boolean = false,
    /** Where the clip that is on is, in ms — null while none is. */
    val playheadMs: Int? = null,
    /** How long the clip that is on runs, in ms — the scrubber's range, and null while none is. */
    val durationMs: Int? = null,
    /** Whether the user has nominated a folder for audio sidecars. */
    val folderChosen: Boolean = false,
    /** Whether standalone audio is saved into public storage on finish. */
    val saveStandalone: Boolean = false,
    /** Current playback speed multiplier. */
    val playbackSpeed: Float = 1.0f,
    /** Extracted audio waveform amplitudes for the active clip. */
    val waveform: List<Float> = emptyList(),
    /** Active transcription for the playing clip if available. */
    val transcription: com.mobixournal.audio.AudioTranscription? = null,
    /** Title or page context of the active audio clip. */
    val clipTitle: String? = null,
    /** Whether the floating mini-player is visible. */
    val miniPlayerVisible: Boolean = false,
    /** Whether speech-to-text deferred transcription is running in background. */
    val isTranscribing: Boolean = false,
    /** In-place status message for deferred transcription. */
    val transcriptionStatus: String? = null,
    /** Whether recording is currently paused. */
    val recordingPaused: Boolean = false,
    /** Callback to start or stop recording. */
    val onToggleRecord: () -> Unit = {},
    /** Callback to pause or resume recording. */
    val onTogglePauseRecord: () -> Unit = {},
    /** Callback to sound the held clip again from where it is. */
    val onResume: () -> Unit = {},
    /** Callback to hold the clip that is sounding where it is. */
    val onPause: () -> Unit = {},
    /** Callback to let the clip go: nothing sounds and the page paints itself again. */
    val onStopPlayback: () -> Unit = {},
    /** Callback to move the playhead to the dragged position, in ms. */
    val onScrub: (Int) -> Unit = {},
    /** Callback to skip forward/backward by [deltaMs]. */
    val onSkip: (Int) -> Unit = {},
    /** Callback to set the playback speed factor. */
    val onSpeedChange: (Float) -> Unit = {},
    /** Configured transcription language tag or "auto". */
    val transcriptionLanguage: String = "auto",
    /** Callback to set transcription language tag. */
    val onLanguageChange: (String) -> Unit = {},
    /** Callback to open the folder picker. */
    val onChooseFolder: () -> Unit = {},
    /** Callback to toggle standalone audio export. */
    val onToggleSaveStandalone: (Boolean) -> Unit = {},
    /** Callback to toggle the mini-player's visibility. */
    val onToggleMiniPlayer: () -> Unit = {},
    /** Callback to start or toggle autonomous audio playback. */
    val onPlayAutonomous: () -> Unit = {},
    /** Callback to transcribe the currently active or selected audio recording. */
    val onTranscribeActiveClip: () -> Unit = {},
)
