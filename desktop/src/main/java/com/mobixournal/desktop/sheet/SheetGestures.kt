package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.PenContactStream
import com.mobixournal.desktop.ink.PenPacketPan
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.shell.DesktopDiagnostics
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DesktopTool

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
    /** The pen-tip ring: while it is up, every press on the page belongs to it. */
    hud: HudUi = HudUi(),
    /** True while the tablet's packets are the pen's voice — they end the contact a ring answered. */
    penVoice: () -> Boolean = { false },
    /**
     * Stops a wheel glide that is still running, called when the hand takes the viewport.
     *
     * Here, in the gesture that is already ours, and **not** from a press handler of the wheel's own: a
     * glide left running writes its target over the pan's first event, and cancel-on-press in the wheel's
     * node took the press with it — the report that the hand did nothing after a scroll.
     */
    cancelGlide: () -> Unit = {},
): Modifier = pointerInput(
    tab, panMode, tool, ink, shapeInk, pen,
    sheet.pageW, sheet.pageH, sheet.zoom, sheet.viewW, sheet.viewH,
    // The press's own answers travel inside [targets], and the block below captures that instance: a
    // variant, a side count or a component picked while the canvas is up is therefore a key of its own,
    // so the handler that reads it is the one started after the answer changed — and never a drag still
    // holding the value its first composition carried.
    targets.figures, targets.circuitKind,
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
        // Where the drag is *drawing* the block, so a sample costs a state write and not a page rewrite.
        liveShift = pointer.liveShift,
        previewRegion = pointer.previewRegion,
        // The highlighter's magnetic preview, shared by both input paths.
        pdfBands = pointer.pdfBands,
        // A pose drag's own reading: the aim pill and the snap tick while the instrument is held.
        guideAim = pointer.guideAim,
        onViewport = onViewport,
        onEdited = onEdited,
        hud = hud,
        penVoice = penVoice,
        cancelGlide = cancelGlide,
    )
}

/**
 * Drag the page by the pointer's movement until it lifts — the pan half of the canvas input.
 *
 * [currentViewport] is read **per event**, never once at the press. Another writer scrolls this same sheet
 * while the drag runs — the pen's packets pan it from the driver's own readings (`PenPacketPan`), and the
 * canvas's edge ticker travels it under a selection — so a position accumulated from the press-time snapshot
 * would put the page back where the gesture began the first time one of them moved: the drag then reads as a
 * bounce instead of a pan.
 *
 * [owned] is the tablet's side of the same drag: while the driver's packets are panning this very contact
 * (`PenPacketPan`), the emulated mouse deltas are **not** applied as well — doing both would pan twice as
 * far per inch of movement, and at the coalesced rate Windows reports a pen that reads as a crawling hand.
 */
internal suspend fun AwaitPointerEventScope.panByDrag(
    currentViewport: () -> Viewport,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    onViewport: (Viewport) -> Unit,
    owned: () -> Boolean = { false },
) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull() ?: return
        if (change.changedToUpIgnoreConsumed()) return
        val drag = change.positionChange()
        if (drag != Offset.Zero) {
            if (owned()) {
                DesktopDiagnostics.panSkipped()
            } else {
                DesktopDiagnostics.panCompose(drag.y / density)
                onViewport(
                    currentViewport().pannedBy(drag.x / density, drag.y / density, pageW, pageH, viewW, viewH),
                )
            }
            change.consume()
        }
    }
}
