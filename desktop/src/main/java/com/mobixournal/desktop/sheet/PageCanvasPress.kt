package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.activeTool
import com.mobixournal.desktop.ink.shapeKind
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import com.mobixournal.render.ActiveTool
import com.mobixournal.render.SelectionRegion

/**
 * What a **press that is not the pan** means, in the order the canvas offers it: a click slot, the PDF text
 * selection, a circuit's pull, a placed instrument, a selection or figure, a geometry slot, the vertical
 * space's grab line, and finally the ink.
 *
 * Split out of the gesture loop so the loop stays the order of a press rather than a list of tools, and the
 * list of tools stays a list (`SourceStructureTest` caps a function's and a file's length). Every branch
 * either takes the drag and returns — the gesture is then its own, and the press is spent — or leaves the
 * press to the next one.
 */
@Suppress("LongParameterList")
internal suspend fun AwaitPointerEventScope.pressAfterPan(
    down: PointerInputChange,
    tool: DesktopTool,
    tab: DesktopTab,
    viewport: Viewport,
    density: Float,
    pageW: Float,
    pageH: Float,
    shapeInk: DesktopInk,
    dragView: DragView,
    targets: PagePressTargets,
    guideAim: MutableState<GuideAim?>?,
    grabLine: MutableState<Double?>?,
    liveShift: MutableState<LiveShift?>?,
    previewRegion: MutableState<SelectionRegion?>?,
    onEdited: (Int) -> Unit,
    taps: TapTracker,
    draft: SnapshotStateList<InkSample>,
    gesture: PageInkGesture,
    packetsOwn: () -> Boolean,
) {
    // A slot whose product is not ink **clicks** instead of drawing — the Text box it opens, the take the
    // Audio slot replays — so the gesture ends at the press and no page edit is recorded.
    if (clickSlotTakesThePress(down, tool, tab, viewport, density, pageW, pageH, targets)) return
    // The **PDF text** slot reads instead of drawing: a press anchors the word under it and the drag
    // extends the range in the page's own reading order. Nothing is written — the selection is view
    // state (`docs/pdf.md`) — so the gesture ends at the lift and no page edit is recorded.
    if (tool == DesktopTool.PDF_TEXT_SELECT) {
        pdfTextDrag(down, tab, dragView)
        return
    }
    // A **circuit** slot pulls the part out along the pen, from the press to the pointer, aimed at the
    // page's pins: its own two ends, not a box — the tablet's rule, and the one a rotated part needs
    // (`CircuitGesture.circuitPress`).
    if (circuitPress(down, tool, tab, shapeInk, dragView, targets, onEdited)) return
    // A press on the placed **instrument** poses it, whatever tool is in hand: the guide is an aid on the
    // page, not a tool that inks, and this is the gesture the guides slot exists for (`GuideGesture`).
    if (guideDrag(down, tool, tab, dragView, targets.onLiveDrag, guideAim)) return
    // A selection, a living element or a figure takes the drag before the ink sees it.
    if (preInkPress(down, tool, tab, dragView, pageW, pageH, previewRegion, onEdited, taps, targets)) return
    tool.shapeKind()?.let { kind ->
        shapeDrag(
            down, kind, tab, shapeInk, dragView, draft, targets.onLiveDrag, onEdited,
            figures = targets.figures,
            page = runCatching { tab.document.page(tab.page) }.getOrNull(),
        )
        return
    }
    // The vertical-space tool reflows the page under a grab line instead of drawing on it, and it needs
    // both of its readings: the line it shows and the offset the sheet replays (`LiveShift`).
    if (tool.activeTool() == ActiveTool.VERTICAL_SPACE && grabLine != null && liveShift != null) {
        verticalSpaceDrag(down, tab, dragView, grabLine, liveShift, onEdited)
        return
    }
    inkDrag(down, gesture, packetsOwn)
}
