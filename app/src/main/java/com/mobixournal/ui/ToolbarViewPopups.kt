package com.mobixournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.render.BackgroundRuling
import com.mobixournal.render.BackgroundRulings
import com.mobixournal.render.GuideKind
import com.mobixournal.render.PageTemplates
import com.mobixournal.render.Stationery
import kotlin.math.roundToInt

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
            val textStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
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
 * Paper-style labels for the background pop-up, paired with the `<background style=…>` value written
 * to the `.xopp` file. Names match desktop Xournal++ verbatim so files round-trip; the renderer draws
 * each in [com.mobixournal.render.BackgroundRenderer].
 */
private val BACKGROUND_STYLES: List<Pair<String, String>> = listOf(
    "plain" to "Plain",
    "lined" to "Lined",
    "ruled" to "Ruled",
    "graph" to "Graph",
    "dotted" to "Dotted",
    // Desktop Xournal++'s isometric paper (`isograph`); its `r1` is the triangle's side.
    "isograph" to "Isometric",
)

/** Ruling-spacing chips, in millimetres — the sizes people actually ask for, 1 mm to 10 mm. */
private val RULE_SPACING_MM = listOf(2.0, 3.0, 5.0, 7.0, 10.0)

/**
 * The page-background chooser, in three short sections: the current page's paper style
 * (plain/lined/ruled/graph/dotted/isometric), the **spacing** its ruling is ruled at, and the
 * stationery presets.
 *
 * Each section is a single row — the styles are chips you scroll along rather than six stacked menu
 * items — so the whole chooser stays short instead of burying spacing and stationery under a list of
 * paper names. The sections run from what the sheet *is* (style) to how it is *ruled* (spacing) to a
 * finished template over the top (stationery), which is the order they are set in.
 *
 * [style] and [config] are the current page's, or null when the page is a PDF/pixmap (no solid sheet
 * to re-rule) — in which case the paper and spacing controls are disabled. Spacing is written as
 * desktop's own `r1` parameter ([BackgroundRuling]), so a page ruled at 7 mm reopens at 7 mm on the
 * desktop rather than silently reverting to the default.
 */
@Composable
internal fun BackgroundPopupButton(
    style: String?,
    config: String?,
    onBackgroundStyle: (String) -> Unit,
    onBackgroundConfig: (String?) -> Unit,
    onStationery: (Stationery) -> Unit,
) {
    val ruling = remember(config) { BackgroundRuling.parse(config) }
    ToolbarPopupButton(
        icon = Icons.Outlined.GridOn,
        contentDescription = "Page background",
    ) { dismiss ->
        MenuHeading("Paper")
        PaperStyleRow(style) { value ->
            onBackgroundStyle(value)
            dismiss()
        }
        MenuHeading("Rule spacing")
        RuleSpacingRow(style, ruling) { mm ->
            onBackgroundConfig(ruling.withPt(BackgroundRuling.KEY_SPACING, mm * PageTemplates.MM_PT).text())
        }
        HorizontalDivider()
        MenuHeading("Stationery")
        PopupChipRow {
            for (kind in Stationery.entries) {
                FilterChip(
                    selected = false,
                    enabled = style != null || kind == Stationery.CORNELL,
                    onClick = { onStationery(kind); dismiss() },
                    label = { Text(kind.label) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}

/**
 * The paper styles as one scrollable row of chips, the live one selected. A row rather than six menu
 * items because the choice is a single either/or: this way it costs one line, not six, and the
 * sections below stay in view. Disabled on a page with no solid sheet (a PDF or pixmap).
 */
@Composable
private fun PaperStyleRow(style: String?, onPick: (String) -> Unit) {
    PopupChipRow {
        for ((value, label) in BACKGROUND_STYLES) {
            FilterChip(
                selected = value == style,
                enabled = style != null,
                onClick = { onPick(value) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

/**
 * The spacing choices: a row of millimetre chips — the tapped one written straight to the page — with
 * a compact field for any other value at the end of the same row. The chips mark the spacing the page
 * currently rules at, so the menu says which one is live instead of leaving the user to remember.
 */
@Composable
private fun RuleSpacingRow(
    style: String?,
    ruling: BackgroundRuling,
    onSpacingMm: (Double) -> Unit,
) {
    if (style == null) {
        MenuHint("No ruling to space on a PDF or image page")
        return
    }
    val currentMm = BackgroundRulings.spacingPt(style, ruling) / PageTemplates.MM_PT
    var text by remember(currentMm) { mutableStateOf(String.format(java.util.Locale.US, "%.1f", currentMm)) }
    PopupChipRow {
        for (mm in RULE_SPACING_MM) {
            FilterChip(
                selected = kotlin.math.abs(currentMm - mm) < 0.05,
                onClick = { onSpacingMm(mm) },
                label = { Text("${mm.toInt()} mm") },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                // Millimetres, one decimal: anything else is a typo, and a spacing of zero would
                // divide the ruling into an infinite number of lines.
                val clean = typed.filter { it.isDigit() || it == '.' }.take(5)
                text = clean
                clean.toDoubleOrNull()?.takeIf { it in 0.5..50.0 }?.let(onSpacingMm)
            },
            singleLine = true,
            // A suffix rather than a floating label keeps the field one line tall, so it reads as part
            // of the chip row it sits in.
            suffix = { Text("mm") },
            modifier = Modifier
                .width(104.dp)
                .padding(end = 4.dp),
        )
    }
}

/**
 * The one-line container a pop-up uses for a run of chips: horizontally scrollable, edge-padded, so a
 * long row (six paper styles, five spacings plus a field) never wraps the menu taller.
 */
@Composable
private fun PopupChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The audio slot: start/stop the recording that new strokes are stamped against, play/pause/stop the clip
 * that is on, scrub it, and nominate the folder sidecar `.wav` files are kept in. The button turns
 * primary-coloured while the microphone is live, because a forgotten recording is the one mistake here
 * that costs the user something (battery, privacy, a pile of stamped strokes).
 *
 * The **scrubber** is the audio-to-ink direction of mode 2 (`docs/audio-inking-sync.md`): dragging it
 * moves the playhead, and the canvas shows the writing as it stood at that moment — through the still
 * page while a clip is held, and live while one sounds. It is only offered while a clip is on, because
 * a position with no recording behind it is a position in nothing.
 */
@Composable
internal fun AudioPopupButton(state: AudioUiState) {
    ToolbarPopupButton(
        icon = if (state.recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
        contentDescription = "Audio",
        tint = if (state.recording || state.playing) MaterialTheme.colorScheme.primary
        else LocalContentColor.current,
    ) { dismiss ->
        MenuHeading("Audio")
        DropdownMenuItem(
            text = { Text(if (state.recording) "Stop recording" else "Record") },
            leadingIcon = {
                Icon(
                    if (state.recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = null,
                )
            },
            onClick = { state.onToggleRecord(); dismiss() },
        )
        DropdownMenuItem(
            text = { Text("Play") },
            enabled = state.clipOn && !state.playing,
            leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
            onClick = { state.onResume(); dismiss() },
        )
        DropdownMenuItem(
            text = { Text("Pause") },
            enabled = state.playing,
            leadingIcon = { Icon(Icons.Outlined.Pause, contentDescription = null) },
            onClick = { state.onPause(); dismiss() },
        )
        DropdownMenuItem(
            text = { Text(if (state.clipOn) "Stop playing" else "Stop playback") },
            enabled = state.clipOn,
            leadingIcon = { Icon(Icons.Outlined.Stop, contentDescription = null) },
            onClick = { state.onStopPlayback(); dismiss() },
        )
        if (state.clipOn) {
            DropdownMenuItem(
                text = { Text(if (state.miniPlayerVisible) "Hide mini-player" else "Show mini-player") },
                leadingIcon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
                onClick = { state.onToggleMiniPlayer(); dismiss() },
            )
        }
        if (state.clipOn && state.durationMs != null && state.playheadMs != null) {
            AudioScrubber(state)
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Save standalone audio") },
            trailingIcon = {
                androidx.compose.material3.Switch(
                    checked = state.saveStandalone,
                    onCheckedChange = { state.onToggleSaveStandalone(it) },
                )
            },
            onClick = { state.onToggleSaveStandalone(!state.saveStandalone) },
        )
        Text(
            text = "Exports audio file to Music/Recordings in addition to note binding.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).width(220.dp),
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(if (state.folderChosen) "Change audio folder…" else "Choose audio folder…") },
            onClick = { state.onChooseFolder(); dismiss() },
        )
        Text(
            text = if (state.folderChosen) {
                "Recordings are saved beside your .xopp files."
            } else {
                "Recordings stay in the app until you choose a folder next to your .xopp files."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).width(220.dp),
        )
    }
}

/**
 * Where in the clip we are, and a way to move it: the position in `m:ss`, a slider over the clip's own
 * length, and the same reading again in milliseconds so a drag is exact rather than a guess at a pixel.
 *
 * The slider is **stateless in the app's terms** — it reads [AudioUiState.playheadMs] and reports every
 * drag through [AudioUiState.onScrub] — so the picture on the canvas and the knob cannot disagree: both
 * are the session's own playhead, which is also what the frame loop paints with.
 */
@Composable
private fun AudioScrubber(state: AudioUiState) {
    val duration = state.durationMs ?: return
    val position = state.playheadMs ?: return
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).width(240.dp)) {
        Text(
            text = "${timeLabel(position)} / ${timeLabel(duration)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = position.toFloat(),
            onValueChange = { state.onScrub(it.toInt()) },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
        )
    }
}

/** [ms] as `m:ss`, the way a player shows a position — the recording's length is never an hour here. */
private fun timeLabel(ms: Int): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * Everything the [AudioPopupButton] needs, bundled so the rail's parameter list stays readable.
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
)
