package com.mobixournal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.mobixournal.render.clearTextSelection
import com.mobixournal.render.copyTextSelection

/**
 * **Both of a pane's selection bars**, in one place: the element selection's action bar and the PDF text
 * selection's Copy bar.
 *
 * Neither belongs on the bottom edge of the screen — each rides with what it acts on, so the tint that
 * says what is selected is never covered by the controls for it, and a hand holding the stylus does not
 * reach across the page to use them. They are hosted together because they are one decision ("what does
 * this pane float over its own page") and because the canvas passes five numbers and adds one call instead
 * of carrying two branches of its own (`SourceStructureTest` caps a file's length).
 */
@Composable
internal fun PaneSelectionBars(
    pane: PaneState,
    ui: EditorUiState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
) {
    // The element bar: the selection's own options, and Paste when nothing is selected.
    if (pane.selectionRect != null || canPasteOnCanvas(pane, ui)) {
        SelectionActionAnchor(
            pane = pane,
            ui = ui,
            settings = settings,
            onSettingsChange = onSettingsChange,
            canvasSizePx = canvasSizePx,
            modifier = modifier,
        )
    }
    // The PDF text bar: one verb, against the words it copies.
    if (pane.textSelectionRect != null) {
        TextSelectionAnchor(
            pane = pane,
            canvasSizePx = canvasSizePx,
            onCopy = { pane.surface?.copyTextSelection() },
            onDeselect = { pane.surface?.clearTextSelection() },
            modifier = modifier,
        )
    }
}
