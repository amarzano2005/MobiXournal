package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mobixournal.format.SaveFormat
import com.mobixournal.render.ImportPdfMode
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import com.mobixournal.render.captureBackgroundRegion
import com.mobixournal.render.clearBackgroundRegion
import com.mobixournal.render.clearSelection
import com.mobixournal.render.cutBackgroundRegion
import com.mobixournal.render.clearTextSelection
import com.mobixournal.render.copySelection
import com.mobixournal.render.copyTextSelection
import com.mobixournal.render.cancelSpline
import com.mobixournal.render.cutSelection
import com.mobixournal.render.deleteSelection
import com.mobixournal.render.duplicateSelection
import com.mobixournal.render.finishSpline
import com.mobixournal.render.insertPlot
import com.mobixournal.render.insertTable
import com.mobixournal.render.restyleSelection
import com.mobixournal.render.undoLastSplineNode

/**
 * Everything layered over the canvas: the contextual *mode* bars along the bottom edge (the element
 * selection's own action bar is not among them — it floats beside the selection, see
 * [SelectionActionAnchor]), and the authoring/chooser dialogs. Each one is driven by a single flag on
 * [ui] or [pane], so this is where the screen's "what is open right now" logic lives instead of being
 * strewn through the layout.
 */
@Composable
fun BoxScope.EditorOverlays(
    ui: EditorUiState,
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    currentSaveFormat: () -> SaveFormat,
    currentSaveName: () -> String,
    onSaveAs: (filename: String, format: SaveFormat) -> Unit,
    onImportPdf: (ImportPdfMode) -> Unit,
) {
    val surface = pane.surface
    val palette = rememberColorPaletteState(settings, onSettingsChange)
    val barModifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)

    SelectionOverlays(ui, pane, barModifier, settings, onSettingsChange)

    ui.textPlacement?.let { placement ->
        val existing = placement.existing
        val defaults = ui.textDefaults
        TextBoxDialog(
            title = if (existing != null) "Edit text" else "Add text",
            initialContent = existing?.content ?: "",
            initialFamily = existing?.let { com.mobixournal.format.FontDescription.parse(it.font) }?.family ?: defaults.family,
            initialBold = existing?.let { com.mobixournal.format.FontDescription.parse(it.font) }?.bold ?: defaults.bold,
            initialItalic = existing?.let { com.mobixournal.format.FontDescription.parse(it.font) }?.italic ?: defaults.italic,
            initialSize = existing?.size ?: defaults.size,
            initialColor = existing?.color ?: defaults.color,
            palette = palette,
            onConfirm = { content, family, bold, italic, sizePt, colorArgb ->
                surface?.insertText(
                    placement, content, com.mobixournal.format.FontDescription(family, bold, italic).compose(), sizePt, colorArgb
                )
                if (existing == null) {
                    defaults.family = family; defaults.bold = bold; defaults.italic = italic
                    defaults.size = sizePt; defaults.color = colorArgb
                }
                ui.textPlacement = null
            },
            onDismiss = { surface?.cancelTextEdit(); ui.textPlacement = null },
        )
    }
    ui.texPlacement?.let { placement ->
        LatexDialog(
            initial = "",
            onConfirm = { latex -> surface?.insertTex(placement, latex, ui.color); ui.texPlacement = null },
            onDismiss = { ui.texPlacement = null },
        )
    }
    if (ui.showImportPdf) {
        ImportPdfDialog(
            merging = surface?.hasPdfBackground() == true,
            onConfirm = { mode -> ui.showImportPdf = false; onImportPdf(mode) },
            onDismiss = { ui.showImportPdf = false },
        )
    }
    if (ui.showSaveAs) {
        SaveAsDialog(
            initialName = currentSaveName(),
            initialFormat = currentSaveFormat(),
            onConfirm = { filename, format -> ui.showSaveAs = false; onSaveAs(filename, format) },
            onDismiss = { ui.showSaveAs = false },
        )
    }
    if (ui.showScaleneAnglesDialog) {
        ScaleneAnglesDialog(
            angleA = settings.scaleneAngleA,
            angleB = settings.scaleneAngleB,
            angleC = settings.scaleneAngleC,
            onConfirm = { a, b, c ->
                ui.showScaleneAnglesDialog = false
                val updated = settings.copy(
                    triangleKind = TriangleKind.SCALENE,
                    scaleneAngleA = a,
                    scaleneAngleB = b,
                    scaleneAngleC = c,
                )
                onSettingsChange(updated)
                surface?.activateTool(EditorTool.TRIANGLE, ui, updated, onSettingsChange)
            },
            onDismiss = { ui.showScaleneAnglesDialog = false },
        )
    }
    if (ui.showTrapezoidAnglesDialog) {
        TrapezoidAnglesDialog(
            angleA = settings.trapezoidAngleA,
            angleB = settings.trapezoidAngleB,
            onConfirm = { a, b ->
                ui.showTrapezoidAnglesDialog = false
                val updated = settings.copy(
                    trapezoidKind = TrapezoidKind.SCALENE,
                    trapezoidAngleA = a,
                    trapezoidAngleB = b,
                )
                onSettingsChange(updated)
                surface?.activateTool(EditorTool.TRAPEZOID, ui, updated, onSettingsChange)
            },
            onDismiss = { ui.showTrapezoidAnglesDialog = false },
        )
    }
    if (ui.showGraphDialog) {
        FunctionGraphDialog(
            onConfirm = { source, xMin, xMax ->
                ui.showGraphDialog = false
                surface?.insertPlot(source, xMin, xMax)
            },
            onDismiss = { ui.showGraphDialog = false },
        )
    }
    ui.bookmarkPage?.let { page ->
        // Bookmarks are app-side navigation state (see PageBookmarks): the dialog only says what the
        // list became, and the pane persists it against the document it is showing.
        val existing = pane.bookmarks.firstOrNull { it.page == page }
        PageBookmarkDialog(
            page = page,
            existing = existing,
            initialColor = PageBookmarks.nextColor(pane.bookmarks),
            onSave = { label, color ->
                ui.bookmarkPage = null
                pane.onBookmarksChange?.invoke(PageBookmarks.with(pane.bookmarks, page, label, color))
            },
            onDelete = {
                ui.bookmarkPage = null
                pane.onBookmarksChange?.invoke(PageBookmarks.without(pane.bookmarks, page))
            },
            onDismiss = { ui.bookmarkPage = null },
        )
    }
    if (ui.showPenParametersDialog) {
        PenParametersDialog(
            minimumPressure = settings.minimumPressure,
            pressureMultiplier = settings.pressureMultiplier,
            pressureEnabled = settings.pressureEnabled,
            presets = settings.penPresets,
            selectedPresetId = settings.selectedPenPresetId,
            onConfirm = { minP, mult, presets, selectedId ->
                ui.showPenParametersDialog = false
                val updated = settings.copy(
                    minimumPressure = minP,
                    pressureMultiplier = mult,
                    penPresets = presets,
                    selectedPenPresetId = selectedId,
                )
                onSettingsChange(updated)
                surface?.applySettings(updated)
            },
            onDismiss = { ui.showPenParametersDialog = false },
        )
    }
}

/**
 * The selection and placement bars: the contextual action bars for select tools and text selections.
 * Extracted from [EditorOverlays] to keep the dispatcher small.
 */
@Composable
private fun BoxScope.SelectionOverlays(
    ui: EditorUiState,
    pane: PaneState,
    barModifier: Modifier,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val surface = pane.surface
    // The element selection's own bar is no longer here: it floats beside the selection itself
    // (see [SelectionActionAnchor], rendered by each pane). What stays at the bottom edge are the
    // mode bars — the marquee's paste/region actions, table insert, spline and PDF-text selection —
    // none of which belong to a selected element.
    // Paste is deliberately not in this bar: it acts on the document rather than on a region, so it
    // rides with Copy and Cut in the selection action bar ([SelectionActionAnchor]) — where it also
    // stays reachable while something is selected. What is left here is the region bar itself.
    if (!pane.hasSelection && ui.tool in MARQUEE_TOOLS) {
        SelectModeBar(
            modifier = barModifier,
            hasRegion = pane.hasBackgroundRegion,
            onCopyRegion = { surface?.captureBackgroundRegion() },
            onCutRegion = { surface?.cutBackgroundRegion() },
            onClearRegion = { surface?.clearBackgroundRegion() },
        )
    }
    if (pane.splineNodes > 0) {
        SplineModeBar(
            nodeCount = pane.splineNodes,
            onFinish = { surface?.finishSpline() },
            onUndoPoint = { surface?.undoLastSplineNode() },
            onCancel = { surface?.cancelSpline() },
            modifier = barModifier,
        )
    }
    if (pane.hasTextSelection) {
        TextSelectionBar(
            onCopy = { surface?.copyTextSelection() },
            onDeselect = { surface?.clearTextSelection() },
            modifier = barModifier,
        )
    }
    if (ui.tool == EditorTool.TABLE && !pane.hasSelection) {
        TableModeBar(
            rows = settings.tableRows,
            cols = settings.tableCols,
            hasHeader = settings.tableHeader,
            onRowsChange = { onSettingsChange(settings.copy(tableRows = it)) },
            onColsChange = { onSettingsChange(settings.copy(tableCols = it)) },
            onHasHeaderChange = { onSettingsChange(settings.copy(tableHeader = it)) },
            onInsert = { surface?.insertTable(settings.tableRows, settings.tableCols, hasHeader = settings.tableHeader) },
            modifier = barModifier,
        )
    }
}

/**
 * A modal "please wait" note for a document transfer. Reading or writing a file on a mounted remote
 * share (SSHFS, FTP, cloud) can take seconds, so the wait is shown rather than looking like a hang.
 */
@Composable
fun TransferOverlay(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(label)
            }
        }
    }
}
