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

// --- selection: rubber-band / tap to select, drag to move, delete ------------------------------

/** Down in Select mode: clear the other modes and let [SelectionGestureController] take it. */
internal fun DrawingSurfaceView.beginSelect(event: MotionEvent) {
    scrolling = false
    erasing = false
    placing = false
    current = null
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

/** Copy the selected elements to the clipboard (leaves the document and selection unchanged). */
fun DrawingSurfaceView.copySelection() {
    val sel = selection ?: return
    val page = pageAt(sel.pageIndex) ?: return
    clipboard = SelectionOps.elementsAt(page, sel.refs)
    onClipboardChanged?.invoke(clipboard.isNotEmpty())
    // The selection leaves in all four flavours, so pasting into another view — or another app —
    // carries the geometry and the formula source rather than a picture of them.
    publishSelectionToSystemClipboard(clipboard)
}

/** Copy then delete the selection (one undoable edit via [deleteSelection]). */
fun DrawingSurfaceView.cutSelection() {
    if (selection == null) return
    copySelection()
    deleteSelection()
}

/** Whether the clipboard currently holds anything to paste. */
fun DrawingSurfaceView.hasClipboard(): Boolean = clipboard.isNotEmpty()

/**
 * Paste the clipboard onto the visible page, selecting the pasted copies. The copy lands centred on
 * the **current viewport** of the page the user is looking at — not back at the spot it was copied
 * from, which may be many screens away. Falls back to a small offset when the viewport isn't laid
 * out (or the clipboard has no measurable geometry).
 */
fun DrawingSurfaceView.pasteClipboard() {
    // A copy made in another view (or window) arrives through the system clipboard; the in-app
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

/** Down with the text-select tool: anchor the selection at the word nearest the touch. */
internal fun DrawingSurfaceView.beginTextSelect(event: MotionEvent) {
    scrolling = false; erasing = false; placing = false; current = null
    val index = pdfTextIndex ?: return
    val pageIndex = layout.pageAt(event.x + scrollX, event.y + scrollY)?.index ?: return
    val box = layout.boxes.getOrNull(pageIndex) ?: return
    val anchor = index.anchorWord(pageIndex, box.toPtX(event.x, scrollX), box.toPtY(event.y, scrollY)) ?: return
    textSelecting = true
    textSelPage = pageIndex
    textSelAnchor = anchor
    textSelFocus = anchor
    onTextSelectionChanged?.invoke(true)
    render()
}

/** Drag: extend the selection to the word nearest the touch (kept on the anchor's page). */
internal fun DrawingSurfaceView.textSelectMove(event: MotionEvent) {
    val index = pdfTextIndex ?: return
    val box = layout.boxes.getOrNull(textSelPage) ?: return
    val focus = index.anchorWord(textSelPage, box.toPtX(event.x, scrollX), box.toPtY(event.y, scrollY)) ?: return
    if (focus != textSelFocus) { textSelFocus = focus; render() }
}

/** True while a PDF-text selection is active (drives the Copy affordance). */
fun DrawingSurfaceView.hasTextSelection(): Boolean = textSelPage >= 0 && textSelAnchor >= 0

/** Copy the selected PDF text to the Android system clipboard. */
fun DrawingSurfaceView.copyTextSelection() {
    val index = pdfTextIndex ?: return
    if (!hasTextSelection()) return
    val text = index.rangeText(textSelPage, textSelAnchor, textSelFocus)
    if (text.isEmpty()) return
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("PDF text", text))
}

/** Drop the current PDF-text selection (view-only). */
fun DrawingSurfaceView.clearTextSelection() {
    val had = textSelPage >= 0
    textSelecting = false
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
