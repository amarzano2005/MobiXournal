package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.render.DrawingSurfaceDefaults
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Unit of measurement for the page-size dialog.
 *
 * Each unit knows how to convert points (1/72 in) to and from its own scale.
 */
private enum class SizeUnit(val label: String, val perPt: Double) {
    /** Millimetres: 1 pt = 25.4/72 mm. */
    MM("mm", 25.4 / 72.0),
    /** Inches: 1 pt = 1/72 in. */
    IN("in", 1.0 / 72.0),
    /** Points: 1 pt = 1 pt (identity). */
    PT("pt", 1.0);

    /** Convert points to this unit. */
    fun fromPt(pt: Double): Double = pt * perPt
    
    /** Convert a value in this unit to points. */
    fun toPt(value: Double): Double = value / perPt
}

/** Format a unit value for a field: at most one decimal, trailing `.0` dropped. */
private fun fmtDim(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

/**
 * A short label for the current page size: the matching preset's name (either orientation), else the
 * dimensions in millimetres — used as the trailing hint on the "Page size…" menu row.
 */
internal fun pageSizeLabel(widthPt: Double, heightPt: Double): String {
    PAGE_PRESETS.firstOrNull { matchesPageSize(widthPt, heightPt, it.widthPt, it.heightPt) }
        ?.let { return it.name }
    return "${fmtDim(widthPt * SizeUnit.MM.perPt)}×${fmtDim(heightPt * SizeUnit.MM.perPt)} mm"
}

/**
 * The **page format** of the document: its paper (preset tabs A4/A5/Letter/Legal plus **Default**, a
 * unit toggle, width/height fields and a swap button) and its **canvas geometry** — fixed pages, or one
 * of the infinite boards.
 *
 * The two belong together because they answer one question — *what is a page of this document* — and
 * because a geometry is not a per-app preference: a document is written in the shape it was created
 * in, so the choice is made here, next to the paper it replaces, and asked about on confirm
 * ([CanvasScopeDialog]). The paper half is hidden for a board ([sized] false), which has no page to
 * measure; the geometry half is always there, so a board can be turned back into pages.
 *
 * [onConfirm] reports the paper **and** the chosen geometry; the caller decides whether the paper is
 * applied (only when there is a page to apply it to) and asks where the geometry goes.
 * [onSaveAsDefault] stores the paper as the default new documents and pages are born with.
 */
/**
 * The paper half of the page format, as one piece of state: the unit the fields are written in, the
 * size the user has settled on, and the two field strings that carry what they typed (kept separate
 * from the parsed size so a half-typed "20" does not become 20 mm on its way to 200).
 */
private class PaperState(widthPt: Double, heightPt: Double) {
    var unit by mutableStateOf(SizeUnit.MM)
    var widthPt by mutableStateOf(widthPt)
    var heightPt by mutableStateOf(heightPt)
    var widthText by mutableStateOf(fmtDim(unit.fromPt(widthPt)))
    var heightText by mutableStateOf(fmtDim(unit.fromPt(heightPt)))

    fun resync() {
        widthText = fmtDim(unit.fromPt(widthPt))
        heightText = fmtDim(unit.fromPt(heightPt))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PageFormatDialog(
    initialWidthPt: Double,
    initialHeightPt: Double,
    defaultWidthPt: Double,
    defaultHeightPt: Double,
    sized: Boolean,
    canvasMode: CanvasMode,
    /** The bound on a strip's non-infinite axis, or null when nothing is locked. */
    extentPt: Double?,
    extentEditable: Boolean,
    onExtent: (Double) -> Unit,
    onConfirm: (Double, Double, NewDocumentCanvas, Double?) -> Unit,
    onSaveAsDefault: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val paper = remember { PaperState(initialWidthPt, initialHeightPt) }
    var canvas by remember { mutableStateOf(NewDocumentCanvas.of(canvasMode)) }
    var customExtentPt by remember(canvas) { mutableStateOf<Double?>(null) }
    val isPaginated = canvas == NewDocumentCanvas.PAGINATED
    val isStrip = canvas == NewDocumentCanvas.INFINITE_VERTICAL ||
        canvas == NewDocumentCanvas.INFINITE_HORIZONTAL
    val stripIsWidth = canvas == NewDocumentCanvas.INFINITE_VERTICAL
    val initialStripExtentPt = when {
        canvas.mode == canvasMode && extentPt != null -> extentPt
        stripIsWidth -> paper.widthPt
        else -> paper.heightPt
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page format") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // --- the canvas: what a page *is* in this document -------------------------------
                Text("Canvas", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (option in NewDocumentCanvas.entries) {
                        FilterChip(
                            selected = option == canvas,
                            onClick = { canvas = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                Text(
                    "Paged breaks the writing into sheets; the infinite canvases keep one running " +
                        "sheet that grows as you scroll, so nothing is cut at a page edge.",
                    style = MaterialTheme.typography.bodySmall,
                )
                // A strip runs on in one direction and is **fixed** in the other: that fixed dimension
                // is the board's bound, and it is the only paper a board has. Which axis it is follows
                // from the selected mode — a vertical strip locks its width.
                if (isStrip) {
                    HorizontalDivider()
                    FixedExtentField(
                        pt = customExtentPt ?: initialStripExtentPt,
                        isWidth = stripIsWidth,
                        enabled = extentEditable || canvas.mode != canvasMode,
                        onValue = { pt ->
                            customExtentPt = pt
                            if (canvas.mode == canvasMode) onExtent(pt)
                        },
                    )
                }
                if (isPaginated) {
                    HorizontalDivider()
                    PaperSection(paper, defaultWidthPt, defaultHeightPt)
                }
            }
        },
        confirmButton = {
            Row {
                if (isPaginated) {
                    TextButton(onClick = { onSaveAsDefault(paper.widthPt, paper.heightPt) }) {
                        Text("Save as default")
                    }
                }
                TextButton(
                    onClick = { onConfirm(paper.widthPt, paper.heightPt, canvas, customExtentPt) },
                ) {
                    Text("Set")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The paper a page is measured on: preset tabs, the unit the fields are written in, two size fields
 * and the swap button. Only shown when the document has a page to measure ([PageFormatDialog]'s
 * `sized`) — a board's sheet is derived from its mode, not from a paper size.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaperSection(paper: PaperState, defaultWidthPt: Double, defaultHeightPt: Double) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (preset in PAGE_PRESETS) {
            SizeTab(
                label = preset.name,
                selected = matchesPageSize(paper.widthPt, paper.heightPt, preset.widthPt, preset.heightPt),
                onClick = {
                    paper.widthPt = preset.widthPt
                    paper.heightPt = preset.heightPt
                    paper.resync()
                },
            )
        }
        SizeTab(
            label = DEFAULT_PAGE_PRESET,
            selected = matchesPageSize(paper.widthPt, paper.heightPt, defaultWidthPt, defaultHeightPt),
            onClick = {
                paper.widthPt = defaultWidthPt
                paper.heightPt = defaultHeightPt
                paper.resync()
            },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Units:", style = MaterialTheme.typography.labelMedium)
        for (u in SizeUnit.entries) {
            TextButton(onClick = { if (u != paper.unit) { paper.unit = u; paper.resync() } }) {
                Text(
                    u.label,
                    fontWeight = if (u == paper.unit) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SizeField(
            label = "Width",
            text = paper.widthText,
            unit = paper.unit,
            onText = { entered ->
                paper.widthText = entered
                entered.toDoubleOrNull()?.let { paper.widthPt = paper.unit.toPt(it) }
            },
        )
        IconButton(onClick = {
            val w = paper.widthPt
            paper.widthPt = paper.heightPt
            paper.heightPt = w
            paper.resync()
        }) { Icon(Icons.Outlined.SwapHoriz, contentDescription = "Swap width and height") }
        SizeField(
            label = "Height",
            text = paper.heightText,
            unit = paper.unit,
            onText = { entered ->
                paper.heightText = entered
                entered.toDoubleOrNull()?.let { paper.heightPt = paper.unit.toPt(it) }
            },
        )
    }
}

/**
 * The **bound** of an infinite canvas: the size of the one axis its mode does not run on — a vertical
 * strip's width, a horizontal one's height ([CanvasGeometry.fixedExtentPt]).
 *
 * This is the answer to *how wide is an infinite sheet?*: without it a board's width would be whatever
 * paper it happened to be born with, which is a printing decision (A4) standing in for a screen one. It
 * is written in millimetres like every other paper dimension here, and every keystroke that parses is
 * applied at once — the sheet is redrawn from the new bound on the next frame, and a bound of *zero* is
 * ignored rather than shrinking the board to a line.
 *
 * A document that has already been written to a file cannot move its bound here ([enabled] false): a
 * `.mobix`'s shape is its own `canvas.json`'s, and changing the paper under existing ink would rewrite
 * how the stored chunks are read.
 */
@Composable
private fun FixedExtentField(pt: Double, isWidth: Boolean, enabled: Boolean, onValue: (Double) -> Unit) {
    var text by remember(isWidth) { mutableStateOf(fmtDim(SizeUnit.MM.fromPt(pt))) }
    OutlinedTextField(
        value = text,
        onValueChange = { entered ->
            text = entered
            entered.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { mm ->
                onValue(SizeUnit.MM.toPt(mm).coerceIn(
                    DrawingSurfaceDefaults.PAGE_SIZE_MIN_PT,
                    DrawingSurfaceDefaults.PAGE_SIZE_MAX_PT,
                ))
            }
        },
        label = { Text(if (isWidth) "Fixed width" else "Fixed height") },
        suffix = { Text(SizeUnit.MM.label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(180.dp),
    )
    Text(
        "The canvas never scrolls past this edge; the other direction runs on as you write. " +
            if (enabled) "It is the sheet's own size, so it is what the board covers on screen."
            else "A document already saved keeps the shape it was written with.",
        style = MaterialTheme.typography.bodySmall,
    )
}

/** One editable dimension of the paper, in the dialog's current [unit]. */
@Composable
private fun SizeField(label: String, text: String, unit: SizeUnit, onText: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        label = { Text(label) },
        suffix = { Text(unit.label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(120.dp),
    )
}

/** One entry in the page-size preset row: bold while the current size matches the entry. */
@Composable
private fun SizeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
