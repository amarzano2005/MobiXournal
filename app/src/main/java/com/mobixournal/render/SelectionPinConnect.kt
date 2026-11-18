/**
 * A press aimed at one of the **pins a selection wears** — the marks the sheet draws for it — as the join
 * that mark promises: the selected element travels onto the pin, and the part the pin belongs to is not
 * picked.
 *
 * A selected element wears both halves of a pairing (`FigurePins.selectionMarks`): its own pins, and the
 * compatible pins it can be joined to. Until now the second half was only a **promise**: a drag could take
 * the element there, but a press on the mark itself fell through to the ordinary pick, so a tap on the pin
 * selected the component behind it (or, with a placement tool in hand, selected that component instead of
 * placing anything). The mark said "this can join here" and the app answered "you picked that part" — which
 * is the report that the pins could be seen but not used.
 *
 * The rule is the press's, not a new tool: a mark is the more specific target than the ink it sits on, so it
 * is asked **before** the pick — placement pick and selection pick alike — and it is one undoable move that
 * leaves the selection where it was, so the element can be joined again or dragged from there. The pure
 * answer (which mark, how far) is `FigurePins.pressJoin`, unit-tested on the JVM; this file is the one place
 * the tablet turns it into an edit.
 *
 * The window keeps its own press: a pin there is a drag target and a selection grip, and this tap-to-join
 * lives on the tablet, where the marks are read as something to aim at.
 */
package com.mobixournal.render

import android.view.MotionEvent

/**
 * A press at ([event]) joins the selection to the pin it is aimed at, or answers false when it is not aimed
 * at one — in which case the caller's ordinary press (pick, placement, marquee) runs as before.
 *
 * Only a **single** element is joined, which is the selection the marks are drawn for
 * (`CircuitPinOverlay.drawCircuitPins`): a mark on the page belongs to one element's own pin, and a join is a
 * pairing of two ends, never of a crowd.
 */
internal fun DrawingSurfaceView.beginPinConnect(event: MotionEvent, pointerIndex: Int): Boolean {
    val sel = selection ?: return false
    val ref = sel.refs.singleOrNull() ?: return false
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val box = layout.pageAt(x + scrollX, y + scrollY) ?: return false
    // The marks belong to the selection's own page: a press on another sheet is that sheet's press.
    if (box.index != sel.pageIndex) return false
    val page = pageAt(sel.pageIndex) ?: return false
    val join = FigurePins.pressJoin(
        page = page,
        ref = ref,
        xPt = box.toPtX(x, scrollX),
        yPt = box.toPtY(y, scrollY),
        reachPt = CircuitSnapping.thresholdFor(box.scale),
    ) ?: return false
    val before = doc
    val pages = SelectionOps.translate(doc.pages, sel.pageIndex, sel.refs, join.dx, join.dy)
    if (pages === doc.pages) return false
    doc = doc.copy(pages = pages)
    history.record(before)
    notifyHistory()
    relayout()
    render()
    return true
}
