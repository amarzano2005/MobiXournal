package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.mobixournal.render.SelectionRegion

/**
 * The sheet's **pointer handler**, as a modifier of its own — the block `SheetCanvas` would otherwise
 * carry inline, which pushed that composable past the function-length ratchet, and the pointer path is
 * this file's subject anyway.
 *
 * The key is the page and the zoom, **never the offsets**: a drag scrolls the sheet at the canvas's edge,
 * and a key that changed with the viewport would cancel the very gesture that moved it. The view is
 * therefore read through a lambda, which sees the scroll at once.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.sheetGestures(
    tab: DesktopTab,
    panMode: Boolean,
    tool: DesktopTool,
    ink: DesktopInk,
    /** The pen's ink for a geometry slot; [ink] is the picked tool's own. */
    shapeInk: DesktopInk,
    pen: PenContactStream?,
    sheet: SheetLayout,
    pointer: PenPointer,
    draft: SnapshotStateList<InkSample>,
    targets: PagePressTargets,
    onViewport: (Viewport) -> Unit,
    onEdited: (Int) -> Unit,
): Modifier = pointerInput(
    tab, panMode, tool, ink, shapeInk, pen,
    sheet.pageW, sheet.pageH, sheet.zoom, sheet.viewW, sheet.viewH,
) {
    pageGestures(
        tab = tab,
        panMode = panMode,
        tool = tool,
        ink = ink,
        shapeInk = shapeInk,
        targets = targets,
        // The painter's own rule, from the window's live scroll: one derivation for where the sheet is
        // drawn and where a press lands ([SheetLayout.liveViewport]).
        viewport = { sheet.liveViewport(tab.viewport) },
        pageW = sheet.pageW,
        pageH = sheet.pageH,
        viewW = sheet.viewW,
        viewH = sheet.viewH,
        draft = draft,
        pen = pen,
        penActive = { pointer.owns.value },
        grabLine = pointer.grabLine,
        previewRegion = pointer.previewRegion,
        onViewport = onViewport,
        onEdited = onEdited,
    )
}

/**
 * Drag the page by the pointer's movement until it lifts — the pan half of the canvas input.
 *
 * [owned] is the tablet's side of the same drag: while the driver's packets are panning this very contact
 * (`PenPacketPan`), the emulated mouse deltas are **not** applied as well — doing both would pan twice as
 * far per inch of movement, and at the coalesced rate Windows reports a pen that reads as a crawling hand.
 */
internal suspend fun AwaitPointerEventScope.panByDrag(
    viewport: Viewport,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    onViewport: (Viewport) -> Unit,
    owned: () -> Boolean = { false },
) {
    var current = viewport
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull() ?: return
        if (change.changedToUpIgnoreConsumed()) return
        val drag = change.positionChange()
        if (drag != Offset.Zero) {
            if (!owned()) {
                current = current.pannedBy(drag.x / density, drag.y / density, pageW, pageH, viewW, viewH)
                onViewport(current)
            }
            change.consume()
        }
    }
}
