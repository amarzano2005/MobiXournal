/**
 * [DrawingSurfaceView]'s selection surface: starting a rubber-band/tap selection, the PDF-text
 * selection built on the extracted text layer, and every edit that acts on a selection — delete,
 * restyle, copy/cut/paste and duplicate. Extensions on the view, so they read its state directly;
 * the selection itself is owned by [SelectionGestureController] and the element clipboard lives on
 * the view in `DrawingSurfaceView.kt`.
 */
package com.mobixournal.render

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.MotionEvent
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.XoppColor
import com.mobixournal.format.XoppColor.withAlpha

// --- selection: rubber-band / tap to select, drag to move, delete ------------------------------

/** Down in Select mode: clear the other modes and let [SelectionGestureController] take it. */
internal fun DrawingSurfaceView.beginSelect(event: MotionEvent) {
    scroll.active = false
    erasing = false
    placing = false
    ink.current = null
    gestures.beginSelect(event)
}

/** Delete every selected element as one undoable edit. */
fun DrawingSurfaceView.deleteSelection() {
    val sel = selection ?: return
    val before = doc
    doc = doc.copy(pages = SelectionOps.delete(doc.pages, sel.pageIndex, sel.refs))
    selection = null
    onSelectionChanged?.invoke(false)
    history.record(before)
    notifyHistory()
    relayout()
    render()
}

/** Drop the current selection (a view-only change; not recorded in history). */
fun DrawingSurfaceView.clearSelection() {
    if (selection == null) return
    gestures.clearSelection()
    render()
}

/** Recolour and/or re-width the selected elements as one undoable edit (selection stays). */
fun DrawingSurfaceView.restyleSelection(color: Int?, widthPt: Double?) {
    val sel = selection ?: return
    val before = doc
    val pages = SelectionOps.restyle(doc.pages, sel.pageIndex, sel.refs, color, widthPt)
    if (pages === doc.pages) return
    doc = doc.copy(pages = pages)
    history.record(before)
    notifyHistory()
    relayout()
    render()
}

/** Align the selected elements to the selection's bounding box (one undoable edit; selection stays). */
fun DrawingSurfaceView.alignSelection(alignment: SelectionOps.SelectionAlign) {
    val sel = selection ?: return
    val before = doc
    val pages = SelectionOps.align(doc.pages, sel.pageIndex, sel.refs, alignment)
    if (pages === doc.pages) return
    doc = doc.copy(pages = pages)
    history.record(before)
    notifyHistory()
    relayout()
    render()
}

/** Spread the selected elements evenly along one axis (one undoable edit; selection stays). */
fun DrawingSurfaceView.distributeSelection(horizontal: Boolean) {
    val sel = selection ?: return
    val before = doc
    val pages = SelectionOps.distribute(doc.pages, sel.pageIndex, sel.refs, horizontal)
    if (pages === doc.pages) return
    doc = doc.copy(pages = pages)
    history.record(before)
    notifyHistory()
    relayout()
    render()
}

// --- the element clipboard: copy, cut, paste, duplicate ----------------------------------------

/**
 * Copy the selected elements to the app's clipboard (leaves the document and selection unchanged).
 *
 * It deliberately does **not** write Android's system clipboard. A picture copied to duplicate it is not
 * text, but the platform cannot know that: from Android 13 on, every `setPrimaryClip` raises the system's
 * copy banner over the page — the floating word-and-preview chip that says a *text* string was copied —
 * for a gesture that only ever meant "hold this for the next paste". The clip is the app's own
 * ([SelectionClipboard]), so a copy still pastes into the other pane, and what genuinely leaves the app
 * (a drag out of the canvas) still hands other applications all four flavours.
 */
fun DrawingSurfaceView.copySelection() {
    val sel = selection ?: return
    val page = pageAt(sel.pageIndex) ?: return
    val copied = SelectionOps.elementsAt(page, sel.refs)
    SelectionClipboard.elements = copied
    onClipboardChanged?.invoke(copied.isNotEmpty())
}

/** Copy then delete the selection (one undoable edit via [deleteSelection]). */
fun DrawingSurfaceView.cutSelection() {
    if (selection == null) return
    copySelection()
    deleteSelection()
}

/** Whether the clipboard currently holds anything to paste. */
fun DrawingSurfaceView.hasClipboard(): Boolean = SelectionClipboard.elements.isNotEmpty()

/**
 * Paste the clipboard onto the visible page, selecting the pasted copies. The copy lands centred on
 * the **current viewport** of the page the user is looking at — not back at the spot it was copied
 * from, which may be many screens away. Falls back to a small offset when the viewport isn't laid
 * out (or the clipboard has no measurable geometry).
 */
fun DrawingSurfaceView.pasteClipboard() {
    val clipboard = SelectionClipboard.elements
    // A copy made in another view (or window) arrives through the system clipboard; the app's own
    // element clipboard is used when it is the source, so the ordinary paste path is unchanged.
    val external = if (clipboard.isEmpty()) systemClipboardElements(context) else emptyList()
    if (external.isNotEmpty()) {
        val target = visiblePageIndex()
        val box = layout.boxes.getOrNull(target)
        val x = if (box != null && width > 0) box.toPtX(width / 2f, scrollX) else 72.0
        val y = if (box != null && height > 0) box.toPtY(height / 2f, scrollY) else 72.0
        val bounds = SelectionOps.boundsOf(external) ?: return
        pasteOnto(target, external.map { SelectionOps.translate(it, x - bounds.left, y - bounds.top) })
        return
    }
    if (clipboard.isEmpty()) return
    val target = visiblePageIndex()
    val bounds = SelectionOps.boundsOf(clipboard)
    val box = layout.boxes.getOrNull(target)
    val elements = if (bounds != null && box != null && width > 0 && height > 0) {
        // Centre the pasted content on the viewport centre of the page in view.
        val dx = box.toPtX(width / 2f, scrollX) - (bounds.left + bounds.right) / 2
        val dy = box.toPtY(height / 2f, scrollY) - (bounds.top + bounds.bottom) / 2
        clipboard.map { SelectionOps.translate(it, dx, dy) }
    } else {
        clipboard.map {
            SelectionOps.translate(it, DrawingSurfaceDefaults.PASTE_OFFSET_PT, DrawingSurfaceDefaults.PASTE_OFFSET_PT)
        }
    }
    pasteOnto(target, elements)
}

/** Duplicate the selection in place (offset a little), selecting the duplicates. */
fun DrawingSurfaceView.duplicateSelection() {
    val sel = selection ?: return
    val page = pageAt(sel.pageIndex) ?: return
    val copies = SelectionOps.elementsAt(page, sel.refs).map {
        SelectionOps.translate(it, DrawingSurfaceDefaults.PASTE_OFFSET_PT, DrawingSurfaceDefaults.PASTE_OFFSET_PT)
    }
    pasteOnto(sel.pageIndex, copies)
}

/** Append [elements] to [pageIndex]'s top layer as one undoable edit and select them. */
private fun DrawingSurfaceView.pasteOnto(pageIndex: Int, elements: List<Element>) {
    if (elements.isEmpty()) return
    val before = doc
    val (pages, refs) = SelectionOps.addToTopLayer(doc.pages, pageIndex, elements)
    if (refs.isEmpty()) return
    doc = doc.copy(pages = pages)
    selection = ActiveSelection(pageIndex, refs)
    onSelectionChanged?.invoke(true)
    history.record(before)
    notifyHistory()
    relayout()
    render()
}

// --- PDF text selection -------------------------------------------------------------------------

/** Down with the text-select tool: anchor the selection at the word the touch actually hits. */
internal fun DrawingSurfaceView.beginTextSelect(event: MotionEvent) {
    beginTextSelectAt(event.x, event.y)
}

/**
 * Anchor a PDF text selection at the word under ([x], [y]) in this view's px, answering whether the press
 * actually landed on one. The text-select tool and the **finger's long press** (`PdfLongPress.kt`) both enter
 * here, so a gesture that never had to pick a tool selects exactly what the tool would have.
 *
 * Entering is strict: a press in whitespace starts nothing, instead of grabbing the nearest word from across
 * the page. The slack is a press radius of screen dp, converted at this page's zoom and bounded in page points
 * by `PdfHitTolerance`. Nothing is written, so this is also the route a read-only document keeps.
 */
internal fun DrawingSurfaceView.beginTextSelectAt(x: Float, y: Float): Boolean {
    scroll.active = false; erasing = false; placing = false; ink.current = null
    val index = pdfTextIndex ?: return false
    val pageIndex = layout.pageAt(x + scrollX, y + scrollY)?.index ?: return false
    val box = layout.boxes.getOrNull(pageIndex) ?: return false
    val sheet = PdfSheetRef.of(pageAt(pageIndex) ?: return false) ?: return false
    val range = PdfTextSelection.begin(
        index,
        sheet,
        box.toPtX(x, scrollX),
        box.toPtY(y, scrollY),
        PdfHitTolerance.ofScreenPx(
            PdfHitTolerance.PRESS_SLOP_DP * resources.displayMetrics.density.toDouble(),
            box.scale.toDouble(),
        ),
    ) ?: return false
    textSelecting = true
    textSelPage = pageIndex
    textSelAnchor = range.anchor
    textSelFocus = range.focus
    onTextSelectionChanged?.invoke(true)
    render()
    return true
}

/** Drag: extend the selection to the word nearest the touch, on the sheet the anchor's page draws. */
internal fun DrawingSurfaceView.textSelectMove(event: MotionEvent) {
    val index = pdfTextIndex ?: return
    val box = layout.boxes.getOrNull(textSelPage) ?: return
    val sheet = PdfSheetRef.of(pageAt(textSelPage) ?: return) ?: return
    // Extending keeps the nearest-word answer (a drag over a line's gap must not lose the range), and a
    // pointer over another sheet extends nothing at all.
    val extended = PdfTextSelection.extend(
        index,
        PdfTextRange(sheet, textSelAnchor, textSelFocus),
        sheet,
        box.toPtX(event.x, scrollX),
        box.toPtY(event.y, scrollY),
    )
    if (extended.focus != textSelFocus) { textSelFocus = extended.focus; render() }
}

/**
 * The washed **bands** of the current selection, in this view's own px, in reading order.
 *
 * They come from [PdfSelectionSpans] — the very bands [drawTextSelection] paints — through the page's own
 * box, so the chrome hangs the action bar off the shape the reader sees highlighted instead of off an
 * approximation of it. One rect per line, columns never merged: the bar can be attached to *a line*.
 */
internal fun DrawingSurfaceView.textSelectionScreenSpans(): List<android.graphics.RectF> {
    val index = pdfTextIndex ?: return emptyList()
    if (textSelPage < 0 || textSelAnchor < 0) return emptyList()
    val box = layout.boxes.getOrNull(textSelPage) ?: return emptyList()
    val sheet = textSelectionSheet() ?: return emptyList()
    val words = PdfTextSelection.boxes(index, PdfTextRange(sheet, textSelAnchor, textSelFocus))
    return PdfSelectionSpans.of(words).map { span ->
        android.graphics.RectF(
            (span.left * box.scale + box.leftPx - scrollX).toFloat(),
            (span.top * box.scale + box.topPx - scrollY).toFloat(),
            (span.right * box.scale + box.leftPx - scrollX).toFloat(),
            (span.bottom * box.scale + box.topPx - scrollY).toFloat(),
        )
    }
}

/**
 * The union of the selected passages' bands — the whole passage's box, or null when nothing is selected.
 * The bar no longer hangs from it (it follows the endpoint's line), but the chrome still needs the box for
 * `PaneState` and for a reader that has to know *whether* something is selected.
 */
internal fun DrawingSurfaceView.textSelectionScreenRect(): android.graphics.RectF? {
    val spans = textSelectionScreenSpans()
    if (spans.isEmpty()) return null
    val union = android.graphics.RectF(spans.first())
    for (i in 1 until spans.size) union.union(spans[i])
    return union
}

/**
 * The band the **focus** end of the range is on — the line under the reader's finger — or null when that
 * word's box cannot be mapped. This is what the bar is anchored to: a passage of six lines puts its actions
 * beside the line being extended, not under the middle of the block.
 */
internal fun DrawingSurfaceView.textSelectionActiveScreenSpan(): android.graphics.RectF? {
    val index = pdfTextIndex ?: return null
    if (textSelPage < 0 || textSelFocus < 0) return null
    val box = layout.boxes.getOrNull(textSelPage) ?: return null
    val sheet = textSelectionSheet() ?: return null
    val word = index.words(sheet.pageNo).getOrNull(textSelFocus) ?: return null
    val middle = ((word.top + word.bottom) / 2.0 * box.scale + box.topPx - scrollY).toFloat()
    return textSelectionScreenSpans().firstOrNull { middle >= it.top && middle <= it.bottom }
}

/**
 * The phase the bar reads: absent with no range, **being made** while a pointer holds it, settled once the
 * pointer is let go. The transitions themselves are the shared [PdfTextActionLifecycle]; the surface only
 * reports which state it is in.
 */
internal fun DrawingSurfaceView.textSelectionActionPhase(): PdfTextActionPhase = when {
    !hasTextSelection() -> PdfTextActionPhase.ABSENT
    textSelecting -> PdfTextActionPhase.EXTENDING
    else -> PdfTextActionPhase.SETTLED
}

/** Everything the chrome needs about the PDF text selection, or null when there is none. */
internal fun DrawingSurfaceView.textSelectionReport(): TextSelectionReport? {
    val spans = textSelectionScreenSpans()
    val union = textSelectionScreenRect() ?: return null
    return TextSelectionReport(
        union = union,
        spans = spans,
        active = textSelectionActiveScreenSpan(),
        phase = textSelectionActionPhase(),
    )
}

/**
 * The PDF **sheet** this view's text selection reads its words from: the selected page's own
 * `Background.Pdf.pageNo`, never its position in the notebook — a reordered or repeated sheet would answer
 * another page's words (`PdfSheetRef`).
 */
internal fun DrawingSurfaceView.textSelectionSheet(): PdfSheetRef? =
    if (textSelPage < 0) null else pageAt(textSelPage)?.let { PdfSheetRef.of(it) }

/** True while a PDF-text selection is active (drives the Copy affordance). */
fun DrawingSurfaceView.hasTextSelection(): Boolean = textSelPage >= 0 && textSelAnchor >= 0

/** Copy the selected PDF text to the Android system clipboard. */
fun DrawingSurfaceView.copyTextSelection() {
    val index = pdfTextIndex ?: return
    if (!hasTextSelection()) return
    val sheet = textSelectionSheet() ?: return
    val text = PdfTextSelection.text(index, PdfTextRange(sheet, textSelAnchor, textSelFocus))
    if (text.isEmpty()) return
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("PDF text", text))
}

/**
 * Highlight the selected PDF words: one ordinary highlighter band per line, on the page's active layer, as
 * **one** undo step.
 *
 * The bands are [MagneticHighlighter.selectionStrokes] over the words the reader selected — the same geometry a
 * magnetic highlighter drag commits — so a highlight made by selecting text and one made by dragging the
 * marker are the same ink, round-trip through `.mobix`/`.xopp` the same way, and reach a PDF export as the
 * same `/Highlight` annotation. [color] is the highlighter's own colour; it is stored semi-transparent, as a
 * drawn highlight is.
 *
 * Selection is reading, so this is the first act of the select-text slot that *writes*: it is refused on a
 * read-only document, exactly as the eraser and the pen are, and the selection is left where it was — a reader
 * who wants it again does not have to select the passage twice.
 */
fun DrawingSurfaceView.highlightTextSelection(color: Int): Boolean {
    val index = pdfTextIndex ?: return false
    if (isReadOnly || !hasTextSelection()) return false
    val sheet = textSelectionSheet() ?: return false
    val words = PdfTextSelection.boxes(index, PdfTextRange(sheet, textSelAnchor, textSelFocus))
    if (words.isEmpty()) return false
    val ink = if ((color ushr 24) == 0xFF) color.withAlpha(XoppColor.HIGHLIGHTER_ALPHA) else color
    val strokes = MagneticHighlighter.selectionStrokes(words, ink)
    if (strokes.isEmpty()) return false
    // One snapshot for the whole highlight, taken before the first band lands: many strokes, one undo step.
    val before = doc
    strokes.forEach { appendStroke(textSelPage, it) }
    history.record(before)
    relayout()
    render()
    notifyHistory()
    return true
}

/**
 * **Underline** the selected PDF words: one ordinary pen rule under each line, on the page's active layer, as
 * **one** undo step.
 *
 * The geometry is [PdfTextUnderline]'s — one two-point [com.mobixournal.format.model.Tool.PEN] stroke per line
 * and column segment, placed just below that segment's own word boxes — so the mark is ink rather than a PDF
 * annotation: it round-trips through `.mobix` and `.xopp` and reaches an exported PDF through the ordinary
 * stroke path. [color] and [widthPt] are the **pen's** own colour and slot (not the highlighter's), the policy
 * `docs/pdf.md` states for this verb.
 *
 * Like Highlight it is an act of writing, so it is refused on a read-only document, and the selection stays
 * where it was — a reader who wants it again does not have to select the passage twice. The transient range
 * itself is never saved.
 */
fun DrawingSurfaceView.underlineTextSelection(color: Int, widthPt: Double): Boolean {
    val index = pdfTextIndex ?: return false
    if (isReadOnly || !hasTextSelection()) return false
    val sheet = textSelectionSheet() ?: return false
    val words = PdfTextSelection.boxes(index, PdfTextRange(sheet, textSelAnchor, textSelFocus))
    val strokes = PdfTextUnderline.strokes(words, color, widthPt)
    if (strokes.isEmpty()) return false
    // One snapshot for the whole underline, taken before the first rule lands: many strokes, one undo step.
    val before = doc
    strokes.forEach { appendStroke(textSelPage, it) }
    history.record(before)
    relayout()
    render()
    notifyHistory()
    return true
}

/** Drop the current PDF-text selection (view-only). */
fun DrawingSurfaceView.clearTextSelection() {
    val had = textSelPage >= 0
    textSelecting = false
    textHandleDrag.grab = null
    textSelPage = -1
    textSelAnchor = -1
    textSelFocus = -1
    if (had) {
        onTextSelectionChanged?.invoke(false)
        render()
    }
}

/** If the current selection contains exactly one [PlotElement], returns that element; otherwise null. */
fun DrawingSurfaceView.singleSelectedPlot(): PlotElement? {
    val sel = selection ?: return null
    if (sel.refs.size != 1) return null
    val ref = sel.refs.first()
    val page = pageAt(sel.pageIndex) ?: return null
    return page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? PlotElement
}

/**
 * The page that holds [element], or null when no page does.
 *
 * The selected page is tried first — that is the element's page in the usual case, and it costs one
 * page read. The scan behind it exists because an editor can outlive the selection that opened it (the
 * selection moves to another page while a plot, table or circuit dialog stays open); an edit is about
 * the element, so it lands on the page that holds it rather than on "whatever page is selected now".
 * The scan materialises pages on the way, so it only runs when the selected page came back empty.
 */
internal fun DrawingSurfaceView.pageIndexHolding(element: Element): Int? {
    val sel = selection
    if (sel != null && pageAt(sel.pageIndex)?.let { ElementEdits.findOn(it, element) } != null) {
        return sel.pageIndex
    }
    for (index in 0 until pageSource.pageCount) {
        val page = pageAt(index) ?: continue
        if (ElementEdits.findOn(page, element) != null) return index
    }
    return null
}

/**
 * Replace [old] plot element with [new] in place: records history for undo,
 * keeps the same layer, z-order, and id, and repaints.
 */
fun DrawingSurfaceView.replacePlotElement(old: PlotElement, new: PlotElement) {
    val before = doc
    val index = pageIndexHolding(old) ?: return
    val replaced = windowEditPage(index) { page ->
        ElementEdits.replaceElementOn(page, old, new)
            ?: fallbackReplacePlotOn(page, index, new)
            ?: page
    }
    if (replaced == null) return
    history.record(before)
    notifyHistory()
    relayout()
    render()
}

internal fun DrawingSurfaceView.fallbackReplacePlotOn(page: Page, pageIndex: Int, new: PlotElement): Page? {
    val sel = selection ?: return null
    // The selection's ref only addresses a plot on the page the selection is on.
    if (sel.pageIndex != pageIndex || sel.refs.size != 1) return null
    val ref = sel.refs.first()
    val layer = page.layers.getOrNull(ref.layerIndex) ?: return null
    if (layer.elements.getOrNull(ref.elementIndex) !is PlotElement) return null
    val layers = page.layers.toMutableList()
    val els = layer.elements.toMutableList()
    els[ref.elementIndex] = new
    layers[ref.layerIndex] = Layer(els, layer.name)
    return page.copy(layers = layers)
}

/**
 * Returns the currently selected [TableElement] if the active selection consists of exactly one table.
 */
fun DrawingSurfaceView.singleSelectedTable(): TableElement? {
    val sel = selection ?: return null
    if (sel.refs.size != 1) return null
    val ref = sel.refs.first()
    val page = pageAt(sel.pageIndex) ?: return null
    return page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? TableElement
}

/**
 * Replace [old] table element with [new] in place: records history for undo,
 * keeps the same layer, z-order, and id, and repaints.
 */
fun DrawingSurfaceView.replaceTableElement(old: TableElement, new: TableElement) {
    val before = doc
    val index = pageIndexHolding(old) ?: return
    windowEditPage(index) { page -> ElementEdits.replaceElementOn(page, old, new) ?: page } ?: return
    history.record(before)
    notifyHistory()
    relayout()
    render()
}

/**
 * Replace [old] math block with [new] in place: records one undo step, keeps the same layer,
 * z-order and id, and repaints. The formula dialog edits the block's own source, so the drawing
 * follows from the new element without a stale cache.
 */
fun DrawingSurfaceView.replaceMathBlock(old: MathBlockElement, new: MathBlockElement) {
    val before = doc
    val index = pageIndexHolding(old) ?: return
    windowEditPage(index) { page -> ElementEdits.replaceElementOn(page, old, new) ?: page } ?: return
    history.record(before)
    notifyHistory()
    relayout()
    render()
}

/**
 * Returns the currently selected [ParametricShapeElement] if the active selection consists of
 * exactly one shape — the condition for offering its corner handles and its **Edit shape** action.
 */
fun DrawingSurfaceView.singleSelectedShape(): ParametricShapeElement? {
    val sel = selection ?: return null
    if (sel.refs.size != 1) return null
    val ref = sel.refs.first()
    val page = pageAt(sel.pageIndex) ?: return null
    return page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)
        as? ParametricShapeElement
}

/**
 * Replace [old] shape with [new] in place: records history for undo, keeps the same layer, z-order
 * and id, and repaints. The element is found on the page that actually holds it, so an editor that
 * outlived its selection still edits the right figure.
 */
fun DrawingSurfaceView.replaceShapeElement(old: ParametricShapeElement, new: ParametricShapeElement) {
    val before = doc
    val index = pageIndexHolding(old) ?: return
    windowEditPage(index) { page -> ElementEdits.replaceElementOn(page, old, new) ?: page } ?: return
    history.record(before)
    notifyHistory()
    relayout()
    render()
}

/**
 * Returns the currently selected [CircuitElement] if the active selection consists of exactly one circuit component.
 */
fun DrawingSurfaceView.singleSelectedCircuit(): CircuitElement? {
    val sel = selection ?: return null
    if (sel.refs.size != 1) return null
    val ref = sel.refs.first()
    val page = pageAt(sel.pageIndex) ?: return null
    return page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? CircuitElement
}

/**
 * Replace [old] circuit element with [new] in place: records history for undo,
 * keeps the same layer, z-order, and id, and repaints.
 */
fun DrawingSurfaceView.replaceCircuitElement(old: CircuitElement, new: CircuitElement) {
    val before = doc
    val index = pageIndexHolding(old) ?: return
    val replaced = windowEditPage(index) { page ->
        ElementEdits.replaceElementOn(page, old, new)
            ?: fallbackReplaceCircuitOn(page, index, new)
            ?: page
    }
    if (replaced == null) return
    history.record(before)
    notifyHistory()
    reportSelectionRect()
    relayout()
    render()
}

internal fun DrawingSurfaceView.fallbackReplaceCircuitOn(page: Page, pageIndex: Int, new: CircuitElement): Page? {
    val sel = selection ?: return null
    if (sel.pageIndex != pageIndex || sel.refs.size != 1) return null
    val ref = sel.refs.first()
    val layer = page.layers.getOrNull(ref.layerIndex) ?: return null
    val els = layer.elements.toMutableList()
    if (ref.elementIndex !in els.indices) return null
    els[ref.elementIndex] = new
    val layers = page.layers.toMutableList()
    layers[ref.layerIndex] = Layer(els, layer.name)
    return page.copy(layers = layers)
}

// --- what the chrome already knows ----------------------------------------------------------------

/** The boxes last reported to the chrome, so a frame that moved neither selection reports nothing. */
internal class SelectionReports {
    var strokes: android.graphics.RectF? = null
    var text: TextSelectionReport? = null
}

/**
 * The active selection's bounding box in view px, padded like the drawn marquee, or null when nothing is
 * selected or its page isn't laid out. The same box [drawSelectionBox] outlines, so the chrome can hang
 * actions directly off what the user sees.
 */
fun DrawingSurfaceView.selectionScreenRect(): android.graphics.RectF? {
    val sel = selection ?: return null
    val box = layout.boxes.getOrNull(sel.pageIndex) ?: return null
    val page = pageAt(sel.pageIndex) ?: return null
    val b = SelectionTester.boundsOf(page, sel.refs) ?: return null
    val pad = DrawingSurfaceDefaults.SELECT_PAD_PX
    return android.graphics.RectF(
        (b.left * box.scale + box.leftPx - scrollX).toFloat() - pad,
        (b.top * box.scale + box.topPx - scrollY).toFloat() - pad,
        (b.right * box.scale + box.leftPx - scrollX).toFloat() + pad,
        (b.bottom * box.scale + box.topPx - scrollY).toFloat() + pad,
    )
}

/** Emit [onSelectionRectChanged] if the selection box moved since the last report. */
internal fun DrawingSurfaceView.reportSelectionRect() {
    val rect = selectionScreenRect()
    if (rect != selectionReports.strokes) {
        selectionReports.strokes = rect
        onSelectionRectChanged?.invoke(rect)
    }
}

/** Emit [onTextSelectionReport] if anything about the text selection changed since the last report. */
internal fun DrawingSurfaceView.reportTextSelectionRect() {
    val report = textSelectionReport()
    if (report != selectionReports.text) {
        selectionReports.text = report
        onTextSelectionReport?.invoke(report)
    }
}
