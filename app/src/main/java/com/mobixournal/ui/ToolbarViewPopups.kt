package com.mobixournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * The zoom level popup: shows the current zoom percentage with −/+/reset controls. The label is the
 * only slot face whose content is text rather than a glyph, so it is the one that reads the rail's
 * scale directly ([LocalRailSlotScale]) — a "100%" drawn at full size would not fit a shrunken slot.
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
            // The label shrinks with the slot for the same reason — "100%" has to keep fitting inside.
            val slot = LocalRailSlotSize.current
            Box(
                modifier = Modifier
                    .size(slot)
                    .clip(CircleShape)
                    .clickable(onClick = open)
                    .semantics { contentDescription = "Zoom" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${(zoom * 100).roundToInt()}%",
                    maxLines = 1,
                    softWrap = false,
                    fontSize = ZOOM_LABEL * LocalRailSlotScale.current,
                )
            }
        },
    ) { dismiss ->
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onZoomOut) { Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out") }
            TextButton(onClick = onZoomReset) { Text("${(zoom * 100).roundToInt()}%") }
            IconButton(onClick = onZoomIn) { Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in") }
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
 * The page-background chooser: the current page's paper style (plain/lined/ruled/graph/dotted /
 * isometric), the **spacing** its ruling is ruled at, and the stationery presets.
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
        icon = Icons.Filled.GridOn,
        contentDescription = "Page background",
    ) { dismiss ->
        MenuHeading("Paper")
        for ((value, label) in BACKGROUND_STYLES) {
            DropdownMenuItem(
                text = { Text(label) },
                enabled = style != null,
                trailingIcon = {
                    if (value == style) Icon(Icons.Filled.Check, contentDescription = "selected")
                },
                onClick = { onBackgroundStyle(value); dismiss() },
            )
        }
        MenuHeading("Rule spacing")
        if (style == null) {
            DropdownMenuItem(
                text = { Text("Not available on a PDF or image page") },
                enabled = false,
                onClick = {},
            )
        } else {
            RuleSpacingRow(style, ruling) { mm ->
                onBackgroundConfig(
                    ruling.withPt(BackgroundRuling.KEY_SPACING, mm * PageTemplates.MM_PT).text(),
                )
            }
        }
        MenuHeading("Stationery")
        for (kind in Stationery.entries) {
            DropdownMenuItem(
                text = { Text(kind.label) },
                // A paper preset needs a solid sheet to re-rule; Cornell is drawn, so it works on any page.
                enabled = style != null || kind == Stationery.CORNELL,
                onClick = { onStationery(kind); dismiss() },
            )
        }
    }
}

/**
 * The spacing choices: a row of millimetre chips — the tapped one written straight to the page — and
 * a small field for any other value. The chips mark the spacing the page currently rules at, so the
 * menu says which one is live instead of leaving the user to remember.
 */
@Composable
private fun RuleSpacingRow(
    style: String,
    ruling: BackgroundRuling,
    onSpacingMm: (Double) -> Unit,
) {
    val currentMm = BackgroundRulings.spacingPt(style, ruling) / PageTemplates.MM_PT
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (mm in RULE_SPACING_MM) {
            val chosen = kotlin.math.abs(currentMm - mm) < 0.05
            TextButton(onClick = { onSpacingMm(mm) }) {
                Text(
                    text = "${mm.toInt()} mm",
                    color = if (chosen) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        }
    }
    var text by remember(currentMm) { mutableStateOf(String.format(java.util.Locale.US, "%.1f", currentMm)) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            // Millimetres, one decimal: anything else is a typo, and a spacing of zero would
            // divide the ruling into an infinite number of lines.
            val clean = typed.filter { it.isDigit() || it == '.' }.take(5)
            text = clean
            clean.toDoubleOrNull()?.takeIf { it in 0.5..50.0 }?.let(onSpacingMm)
        },
        label = { Text("Custom (mm)") },
        singleLine = true,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .width(150.dp),
    )
}

/**
 * The drawing-guide pop-up: lay a setsquare or a compass on the page, or take it off again. The
 * guide is an input aid — a finger drags it around and re-poses it by its tip handle, and anything
 * drawn near its edge is ruled onto that edge. Nothing about it is written to the `.xopp` file.
 *
 * The face is a **ruler** (`Straighten`), the one pictogram everybody already reads as "rule a
 * straight line". The slot used to show the same triangle as the Shape recognition slot beside it,
 * which is exactly why neither was recognisable: two different jobs, one glyph.
 */
@Composable
internal fun GuidePopupButton(kind: GuideKind, onKind: (GuideKind) -> Unit) {
    ToolbarPopupButton(
        icon = Icons.Filled.Straighten,
        contentDescription = "Drawing guides",
        tint = if (kind == GuideKind.NONE) LocalContentColor.current
        else MaterialTheme.colorScheme.primary,
    ) { dismiss ->
        MenuHeading("Drawing guide")
        for (option in GuideKind.entries) {
            DropdownMenuItem(
                text = { Text(option.label) },
                trailingIcon = { if (option == kind) Icon(Icons.Filled.Check, contentDescription = "selected") },
                onClick = { onKind(option); dismiss() },
            )
        }
    }
}

/**
 * The audio slot: start/stop the recording that new strokes are stamped against, stop playback, and
 * nominate the folder sidecar `.wav` files are kept in. The button turns primary-coloured while the
 * microphone is live, because a forgotten recording is the one mistake here that costs the user
 * something (battery, privacy, a pile of stamped strokes).
 */
@Composable
internal fun AudioPopupButton(state: AudioUiState) {
    ToolbarPopupButton(
        icon = if (state.recording) Icons.Filled.Stop else Icons.Filled.Mic,
        contentDescription = "Audio",
        tint = if (state.recording || state.playing) MaterialTheme.colorScheme.primary
        else LocalContentColor.current,
    ) { dismiss ->
        MenuHeading("Audio")
        DropdownMenuItem(
            text = { Text(if (state.recording) "Stop recording" else "Record") },
            leadingIcon = {
                Icon(
                    if (state.recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                )
            },
            onClick = { state.onToggleRecord(); dismiss() },
        )
        DropdownMenuItem(
            text = { Text("Stop playback") },
            enabled = state.playing,
            leadingIcon = { Icon(Icons.Filled.Stop, contentDescription = null) },
            onClick = { state.onStopPlayback(); dismiss() },
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
 * Everything the [AudioPopupButton] needs, bundled so the rail's parameter list stays readable.
 *
 * @property recording Whether the microphone is currently recording.
 * @property playing Whether audio playback is in progress.
 * @property folderChosen Whether the user has nominated a folder for audio sidecars.
 * @property onToggleRecord Callback to start or stop recording.
 * @property onStopPlayback Callback to stop playback.
 * @property onChooseFolder Callback to open the folder picker.
 */
data class AudioUiState(
    /** Whether the microphone is currently recording. */
    val recording: Boolean = false,
    /** Whether audio playback is in progress. */
    val playing: Boolean = false,
    /** Whether the user has nominated a folder for audio sidecars. */
    val folderChosen: Boolean = false,
    /** Callback to start or stop recording. */
    val onToggleRecord: () -> Unit = {},
    /** Callback to stop playback. */
    val onStopPlayback: () -> Unit = {},
    /** Callback to open the folder picker. */
    val onChooseFolder: () -> Unit = {},
)
