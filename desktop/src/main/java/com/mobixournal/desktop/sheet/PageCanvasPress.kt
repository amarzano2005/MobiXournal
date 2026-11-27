package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.activeTool
import com.mobixournal.desktop.ink.inkTool
import com.mobixournal.desktop.ink.pagePointOf
import com.mobixournal.desktop.ink.shapeKind
import com.mobixournal.desktop.io.DesktopPdfTextHandles
import com.mobixournal.desktop.io.DesktopPdfTextSelect
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.pickForDrag

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import com.mobixournal.render.ActiveTool
import com.mobixournal.render.PdfHitTolerance
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
    /** The canvas's own pairing for the PDF text's double click, kept apart from the selection tools' one. */
    pdfTaps: TapTracker,
    draft: SnapshotStateList<InkSample>,
    gesture: PageInkGesture,
    packetsOwn: () -> Boolean,
) {
    // A slot whose product is not ink **clicks** instead of drawing — the Text box it opens, the take the
    // Audio slot replays — so the gesture ends at the press and no page edit is recorded.
    if (clickSlotTakesThePress(down, tool, tab, viewport, density, pageW, pageH, targets)) return
    // A press on the page that begins something else drops a PDF range first: a click off the text and Escape
    // are the same command, and ink must never be laid over the wash (`docs/pdf.md`). The PDF text entry is its
    // own dismissal — its press re-anchors or misses, and a miss clears the range with the notice that says why.
    if (tab.pdfTextSelection != null && tool != DesktopTool.PDF_TEXT_SELECT) dismissSelection(tab)
    // The **PDF text** slot reads instead of drawing: a press anchors the word under it and the drag
    // extends the range in the page's own reading order. Nothing is written — the selection is view
    // state (`docs/pdf.md`) — so the gesture ends at the lift and no page edit is recorded.
    if (tool == DesktopTool.PDF_TEXT_SELECT) {
        pdfTextDrag(down, tab, dragView)
        return
    }
    // A press with a **placement slot** on the object that slot makes picks it up — and the same press
    // then drags it (`placementPress`), instead of placing a second object on top of the one already there.
    // Paper that holds none of them lets go of the selection and falls through to the slot's own gesture.
    // The canvas's own tracker and editor go with it: the pick hands the press to the selection's gesture,
    // and a plot or a formula must still open its editor on a double click.
    if (
        placementPress(
            down, tool, tab, dragView, density, targets.onLiveDrag, onEdited, targets.onEditLiving, taps,
            targets.circuitKind,
        )
    ) return
    // A **circuit** slot pulls the part out along the pen, from the press to the pointer, aimed at the
    // page's pins: its own two ends, not a box — the tablet's rule, and the one a rotated part needs
    // (`CircuitGesture.circuitPress`).
    if (circuitPress(down, tool, tab, shapeInk, dragView, targets, onEdited)) return
    // A press on the placed **instrument** poses it, whatever tool is in hand: the guide is an aid on the
    // page, not a tool that inks, and this is the gesture the guides slot exists for (`GuideGesture`).
    if (guideDrag(down, tool, tab, dragView, targets.onLiveDrag, guideAim)) return
    // A selection, a living element or a figure takes the drag before the ink sees it.
    if (preInkPress(down, tool, tab, dragView, pageW, pageH, previewRegion, onEdited, taps, targets)) return
    if (splinePress(down, tool, tab, shapeInk, dragView, density, onEdited, targets.onLiveDrag, targets.onEditLiving, taps)) return
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
    // A mouse **double click** on a PDF word reads it, with whatever ink tool is in hand: reading must not
    // cost a tool switch. Asked last, so every gesture above keeps its press — a grip, an element in front of
    // the text, a figure or the reflow rule outrank the words behind them — and the first click of the pair
    // inks nothing (`PageCanvasPress.kt`, `docs/pdf.md`).
    if (tool.inkTool() != null && pdfWordPress(down, tab, dragView, density, pdfTaps, packetsOwn, gesture)) return
    inkDrag(down, gesture, packetsOwn)
}

/**
 * **Reading a PDF with the mouse**, without picking a tool: a double click on a word selects it.
 *
 * The window's form of the tablet's finger hold, and deliberately the same rule — the *owner* of a press is
 * decided before any ink ([PdfWordPresses.read]), so a grip, a placement, a guide or an element in front of
 * the text keeps its gesture, and the text only answers what nothing else claimed.
 *
 * Two things make it a reading gesture rather than a drawing one:
 *  - the **first click of the pair writes nothing**. A click on a word that drew a dot would leave exactly
 *    the mark a selection must never leave, and withdrawing it afterwards would be an undo step the reader
 *    never asked for. A click *is* the reading press; ink needs a stroke, which is a drag, and a drag is
 *    handed to the tool from the press (`holdReadingClick`);
 *  - only the **mouse** reads this way. The pen's packets keep drawing with the tool in hand, so a pen that
 *    lands on a word is still a pen.
 */

/** What one press on a PDF page is: a reading candidate, and maybe the second of a pair. */
internal data class PdfWordPress(
    /** The press belongs to the page's text, not to the tool: it inks nothing. */
    val reading: Boolean,
    /** The second press of a double click: the reading range starts here and a held drag extends it. */
    val second: Boolean,
)

/**
 * The rule, as a value — the same shape the tablet's `pdfTextPressAllowed` takes, so the two shells can be
 * read side by side and a test can name each condition instead of driving a pointer.
 */
internal object PdfWordPresses {

    /**
     * The press radius, in page points, for this window: the tablet's own 8 dp, at the window's DPI, divided
     * by the zoom so a word is as easy to hit at 30 % as at 300 % (`PdfHitTolerance` bounds the result).
     */
    fun tolerancePt(density: Float, zoom: Float): Double =
        PdfHitTolerance.ofScreenPx(PdfHitTolerance.PRESS_SLOP_DP * density, (zoom * density).toDouble())

    /**
     * Read one press.
     *
     * [isMouse] is the device; [hasPackets] is the pen owning the stroke (`DesktopTool.inksByPackets` while
     * the tablet streams), and a pen that draws never reads. [elementInFront] is a text box, a figure or a
     * living element under the pointer — PDF text lies *behind* the page's own content, so whatever is in
     * front of it answers first. [wordUnder] is the strict hit: whitespace is not a word, and a press there
     * leaves the press to the tool rather than starting a range on the nearest word from across the page.
     * [second] is the double-click tracker's answer, which only means something for a reading press.
     */
    fun read(
        isMouse: Boolean,
        hasPackets: Boolean,
        elementInFront: Boolean,
        wordUnder: Boolean,
        second: Boolean,
    ): PdfWordPress {
        val reading = isMouse && !hasPackets && !elementInFront && wordUnder
        return PdfWordPress(reading = reading, second = reading && second)
    }
}

/**
 * Take the press for the page's text when it is a mouse reading press. True means this gesture owns it.
 *
 * [taps] is the canvas's **own** tracker for this gesture (not the selection tools' one): the two pairings
 * must not consume each other's presses, or a click with the pen followed by a click with the marquee would
 * read as one double click.
 */
@Suppress("LongParameterList")
internal suspend fun AwaitPointerEventScope.pdfWordPress(
    down: PointerInputChange,
    tab: DesktopTab,
    view: DragView,
    density: Float,
    taps: TapTracker,
    packetsOwn: () -> Boolean,
    gesture: PageInkGesture,
): Boolean {
    if (down.type != PointerType.Mouse) return false
    if (tab.pdfIndex == null) return false
    val viewport = view.current()
    val (x, y) = pagePointOf(down.position.x, down.position.y, density, viewport)
    if (x !in 0.0..view.pageW.toDouble() || y !in 0.0..view.pageH.toDouble()) return false
    val tol = PdfWordPresses.tolerancePt(density, viewport.zoom)
    val page = runCatching { tab.document.page(tab.page) }.getOrNull()
    val pxPerPt = viewport.zoom * density
    val press = PdfWordPresses.read(
        isMouse = true,
        hasPackets = packetsOwn(),
        elementInFront = page != null && pickForDrag(page, x, y, tol) != null,
        wordUnder = DesktopPdfTextSelect.begin(tab, x, y, tol) != null,
        second = taps.isSecond(System.currentTimeMillis(), x * pxPerPt, y * pxPerPt),
    )
    if (!press.reading) return false
    if (press.second) {
        // The second press anchors the word under it and a held drag extends the range in reading order —
        // the text slot's own gesture, verbatim, so the two doors cannot disagree about what a selection is.
        pdfTextDrag(down, tab, view)
        return true
    }
    return holdReadingClick(down, gesture, packetsOwn)
}

/**
 * The first click of the pair: it writes nothing, until it turns out to be a drag.
 *
 * A drag is the tool's — the same stroke it would have drawn had it started anywhere — so it is handed to the
 * ink router **from the press**, which is why the stroke still contains its first sample. A lift without
 * travel ends the gesture with nothing written: no dot, no page edit, no undo step.
 */
private suspend fun AwaitPointerEventScope.holdReadingClick(
    down: PointerInputChange,
    gesture: PageInkGesture,
    packetsOwn: () -> Boolean,
): Boolean {
    val slop = viewConfiguration.touchSlop
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull() ?: return true
        if (change.changedToUpIgnoreConsumed() || !change.pressed) return true
        if ((change.position - down.position).getDistance() > slop) {
            inkDrag(down, gesture, packetsOwn)
            return true
        }
    }
}

/**
 * A drag that **adjusts** an existing PDF text selection by one of its endpoint handles.
 *
 * It is asked before the pan and before the tool in hand, because a press on a dot is a press on a control:
 * panning the page or drawing under the very handle the reader is reaching for is the gesture the range's
 * handles exist to prevent. False when the press missed both dots, and then the gesture carries on exactly as
 * it would have — the check costs a hit test and changes nothing else.
 *
 * A **pen** never grabs a handle. A stylus press is the tool's, the rule the reading double click already keeps
 * (the workstation's tablet reaches Compose as a mouse copy, so preventing a stray stroke is the pen's owner's
 * job); a mouse or a touch drag moves the passage, and the keyboard reaches the same move from the handle's
 * focus.
 *
 * The capsule waits for the lift: [DesktopTab.pdfTextSelecting] is set for the drag and cleared in `finally`, so
 * the Copy/Highlight bar is [com.mobixournal.render.PdfTextActionPhase.EXTENDING] until the range is settled
 * again — the same phase a range being drawn passes through, not a second flag.
 */
internal suspend fun AwaitPointerEventScope.pdfTextHandleDrag(
    down: PointerInputChange,
    tab: DesktopTab,
    view: DragView,
): Boolean {
    if (down.type == PointerType.Stylus) return false
    val (x, y) = pagePointOf(down.position.x, down.position.y, density, view.current())
    val grab = DesktopPdfTextHandles.grab(tab, x, y, density, view.current().zoom) ?: return false
    tab.pdfTextSelecting = true
    try {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            val (px, py) = pagePointOf(change.position.x, change.position.y, density, view.current())
            DesktopPdfTextHandles.dragTo(tab, grab, px, py)
            if (change.changedToUpIgnoreConsumed() || !change.pressed) break
            change.consume()
        }
    } finally {
        tab.pdfTextSelecting = false
    }
    return true
}
