package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
internal enum class SizeUnit(val label: String, val perPt: Double) {
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
internal fun fmtDim(value: Double): String {
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
    defaultCanvas: NewDocumentCanvas = NewDocumentCanvas.PAGINATED,
    sized: Boolean,
    canvasMode: CanvasMode,
    /** The bound on a strip's non-infinite axis, or null when nothing is locked. */
    extentPt: Double?,
    extentEditable: Boolean,
    onExtent: (Double) -> Unit,
    onConfirm: (Double, Double, NewDocumentCanvas) -> Unit,
    onSaveAsDefault: (Double, Double) -> Unit,
    onSaveDefaultSettings: (Double, Double, NewDocumentCanvas) -> Unit = { w, h, _ -> onSaveAsDefault(w, h) },
    onDismiss: () -> Unit,
) {
    val startWidthPt = if ((canvasMode == CanvasMode.INFINITE_VERTICAL || canvasMode == CanvasMode.INFINITE_2D) && extentPt != null) extentPt else initialWidthPt
    val startHeightPt = if (canvasMode == CanvasMode.INFINITE_HORIZONTAL && extentPt != null) extentPt else initialHeightPt
    val paper = remember(startWidthPt, startHeightPt) { PaperState(startWidthPt, startHeightPt) }
    var canvas by remember(canvasMode) { mutableStateOf(NewDocumentCanvas.of(canvasMode)) }
    val defaultPaper = remember(defaultWidthPt, defaultHeightPt) { PaperState(defaultWidthPt, defaultHeightPt) }
    var defaultCanvasChoice by remember(defaultCanvas) { mutableStateOf(defaultCanvas) }
    var inDefaultSection by remember { mutableStateOf(false) }

    com.mobixournal.ui.theme.FloatingDialog(
        onDismissRequest = onDismiss,
        title = {
            if (inDefaultSection) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { inDefaultSection = false }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Default settings")
                }
            } else {
                Text("Page format")
            }
        },
        text = {
            if (inDefaultSection) {
                DefaultFormatContent(defaultPaper, defaultCanvasChoice, onSelectCanvas = { defaultCanvasChoice = it })
            } else {
                ActiveFormatContent(paper, canvas, onSelectCanvas = { canvas = it })
            }
        },
        confirmButton = {
            if (inDefaultSection) {
                DefaultFormatActionButtons(
                    onBack = { inDefaultSection = false },
                    onCancel = onDismiss,
                    onSave = {
                        onSaveDefaultSettings(defaultPaper.widthPt, defaultPaper.heightPt, defaultCanvasChoice)
                        inDefaultSection = false
                    },
                )
            } else {
                ActiveFormatActionButtons(
                    onOpenDefaults = { inDefaultSection = true },
                    onCancel = onDismiss,
                    onSaveAsDefault = { onSaveAsDefault(paper.widthPt, paper.heightPt) },
                    onSet = { onConfirm(paper.widthPt, paper.heightPt, canvas) },
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFormatContent(
    paper: PaperState,
    canvas: NewDocumentCanvas,
    onSelectCanvas: (NewDocumentCanvas) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Canvas", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (option in NewDocumentCanvas.entries) {
                FilterChip(
                    selected = option == canvas,
                    onClick = { onSelectCanvas(option) },
                    label = { Text(option.label) },
                )
            }
        }
        Text(
            "Paged breaks the writing into sheets; the infinite canvases keep one running " +
                "sheet that grows as you scroll, so nothing is cut at a page edge.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        PaperSection(paper, canvas)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultFormatContent(
    defaultPaper: PaperState,
    defaultCanvas: NewDocumentCanvas,
    onSelectCanvas: (NewDocumentCanvas) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Default canvas", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (option in NewDocumentCanvas.entries) {
                FilterChip(
                    selected = option == defaultCanvas,
                    onClick = { onSelectCanvas(option) },
                    label = { Text(option.label) },
                )
            }
        }
        Text(
            "The default canvas geometry used when creating new documents.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text("Default dimensions", style = MaterialTheme.typography.labelMedium)
        PaperSection(defaultPaper, defaultCanvas)
    }
}

@Composable
private fun ActiveFormatActionButtons(
    onOpenDefaults: () -> Unit,
    onCancel: () -> Unit,
    onSaveAsDefault: () -> Unit,
    onSet: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onOpenDefaults,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Default settings", fontWeight = FontWeight.SemiBold)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = onSaveAsDefault) { Text("Set as default") }
            TextButton(onClick = onSet) { Text("Set") }
        }
    }
}

@Composable
private fun DefaultFormatActionButtons(
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onSave) { Text("Save default") }
        }
    }
}

/**
 * The paper dimensions and presets for the selected [canvas] mode: preset tabs, unit selector, and
 * the relevant width/height field(s) for that mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaperSection(
    paper: PaperState,
    canvas: NewDocumentCanvas,
) {
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
    val showWidth = canvas != NewDocumentCanvas.INFINITE_HORIZONTAL
    val showHeight = canvas != NewDocumentCanvas.INFINITE_VERTICAL
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showWidth) {
            SizeField(
                label = if (canvas == NewDocumentCanvas.INFINITE_VERTICAL) "Fixed width" else "Width",
                text = paper.widthText,
                unit = paper.unit,
                onText = { entered ->
                    paper.widthText = entered
                    entered.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                        paper.widthPt = paper.unit.toPt(it)
                    }
                },
            )
        }
        if (showWidth && showHeight) {
            IconButton(onClick = {
                val w = paper.widthPt
                paper.widthPt = paper.heightPt
                paper.heightPt = w
                paper.resync()
            }) { Icon(Icons.Outlined.SwapHoriz, contentDescription = "Swap width and height") }
        }
        if (showHeight) {
            SizeField(
                label = if (canvas == NewDocumentCanvas.INFINITE_HORIZONTAL) "Fixed height" else "Height",
                text = paper.heightText,
                unit = paper.unit,
                onText = { entered ->
                    paper.heightText = entered
                    entered.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                        paper.heightPt = paper.unit.toPt(it)
                    }
                },
            )
        }
    }
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
        modifier = Modifier.width(140.dp),
    )
}

/** One entry in the page-size preset row: bold while the current size matches the entry. */
@Composable
private fun SizeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
