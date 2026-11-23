/**
 * What a press with a **top-bar placement tool** does with it: an object of that tool's own making under
 * the point is *selected*, and only paper that holds none takes a new one.
 *
 * Every top-bar element is a *placement* mode, so before this rule a press reserved a new object whatever
 * lay under it — a tap on a resistor with the resistor tool aimed to size it placed a second one on top,
 * and the same was true of a rectangle with the rectangle tool. The rule is the one a drawing tool cannot
 * express: the point decides. A press on a table with the Table tool selects that table, a press on paper
 * places one, and the same press that selected an object is handed to the selection so its handles can be
 * dragged at once — which is what makes an element **inserted and then sized** one gesture and not three.
 *
 * The tool stays in hand across the insertion, so a row of parts, rectangles or gates is placed without
 * picking the slot again ([com.mobixournal.ui.EditorTool]'s own family, `LIVING_ELEMENT_TOOLS`, and the
 * figure tools keep their slot for the same reason).
 *
 * Two vocabularies answer "the same typology", and each is the model's own rather than a list written
 * here. A **living** element (a table, a circuit part) is matched by family — [LivingEditFamily], the
 * double-tap's vocabulary, so any part tool picks any part. A **figure** was drawn as a stroke, and
 * `mobix-core`'s [FigurePins.isFigureOf] answers from the kind the [ShapeBuilder] tool minted into it, so
 * the triangle tool picks the triangles and the rectangle tool picks the rectangles.
 *
 * The **LaTeX** slot places a formula the same way, though it is a placement mode rather than a shape
 * ([PlaceKind.TEX]): its tap claims the formula it made ([pickMathBlock]) so the editor opens on it instead
 * of a second formula landing on top — the window's plot and formula slots, one shell's answer each.
 *
 * Pure page geometry, so the pick is unit-tested on the JVM (`PlacementPickTest`).
 */
package com.mobixournal.render

import android.view.MotionEvent
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.Stroke
import kotlin.math.hypot

/**
 * The family a top-bar **living element** tool places, or null for a tool that draws a figure instead.
 *
 * The shape kinds that put a *living* object on the page are exactly the table and the circuit symbols
 * ([CircuitElementFactory.isCircuitKind]); every other shape tool draws a stroke and is matched by its
 * kind ([pickFigureElement]).
 */
internal fun livingFamilyOf(kind: ShapeKind): LivingEditFamily? = when {
    kind == ShapeKind.TABLE -> LivingEditFamily.TABLE
    CircuitElementFactory.isCircuitKind(kind) -> LivingEditFamily.CIRCUIT
    else -> null
}

/** The topmost element of [kind]'s family under ([xPt], [yPt]) on [page], or null. */
internal fun pickLivingElement(page: Page, xPt: Double, yPt: Double, kind: ShapeKind): ElementRef? {
    val family = livingFamilyOf(kind) ?: return null
    return SelectionTester.pickTopmost(page, xPt, yPt) { family.matches(it) }
}

/**
 * The **formula** under ([xPt], [yPt]) on [page], or null: what the LaTeX slot edits instead of laying a
 * second formula on the one it already made.
 *
 * The LaTeX slot stays in hand after it inserts, so its tap is how the next formula is put down — and that
 * tap used to ask for the dialog again whatever lay under it. A formula is a living element like a table or a
 * part, so it is matched by its family ([LivingEditFamily.MATH], the double-tap's vocabulary) rather than by
 * the element type spelled here.
 */
internal fun pickMathBlock(page: Page, xPt: Double, yPt: Double): MathBlockElement? {
    val ref = SelectionTester.pickTopmost(page, xPt, yPt) { LivingEditFamily.MATH.matches(it) } ?: return null
    return page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? MathBlockElement
}

/**
 * The **plot** under ([xPt], [yPt]) on [page], or null: what the *Function graph* slot picks instead of asking
 * for a formula again and laying a second graph on the first.
 *
 * A plot is a living element like a table or a formula, so it is matched by its family
 * ([LivingEditFamily.PLOT], the double-tap's vocabulary) rather than by the element type spelled here.
 */
internal fun pickPlot(page: Page, xPt: Double, yPt: Double): PlotElement? =
    pickPlotRef(page, xPt, yPt)?.let { ref ->
        page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? PlotElement
    }

/** The reference of the plot under ([xPt], [yPt]), or null — the pick the press needs to **select** one. */
internal fun pickPlotRef(page: Page, xPt: Double, yPt: Double): ElementRef? =
    SelectionTester.pickTopmost(page, xPt, yPt) { LivingEditFamily.PLOT.matches(it) }

/**
 * The topmost figure [kind] draws under ([xPt], [yPt]) on [page], or null.
 *
 * A figure the tool wrote **names its kind** (`FigurePins.figureAttrs`), which is what makes a triangle
 * tool pick a triangle and leave a rectangle alone; a stroke written before that answer existed falls back
 * to its geometry ([FigurePins.isFigureOf]).
 *
 * A figure written as **several strokes** is never picked: a dimension is two halves around its gap, and
 * the model gives the parts no identity to be gathered by, so selecting one half would select half an
 * object. The tool goes on drawing there, as it did before the rule.
 */
internal fun pickFigureElement(page: Page, xPt: Double, yPt: Double, kind: ShapeKind): ElementRef? {
    if (ShapeBuilder.isMultiStroke(kind)) return null
    return SelectionTester.pickTopmost(page, xPt, yPt) {
        it is Stroke && FigurePins.isFigureOf(it, kind)
    }
}

/**
 * The **pin** a press at ([xPt], [yPt]) is aimed at, or null: the first of the pins this gesture would be
 * anchored to ([CircuitSnapping.draftTargets], the very list the draft's magnet snaps the first sample to)
 * within [reachPt] — the same reach the stroke uses.
 *
 * A pin is a **terminal**, not a piece of the object it belongs to: asking this before the pick is what lets a
 * press on a mark start the stroke *on* the pin, so a wire leaves the terminal, instead of selecting the part
 * the mark sits on. Only a pin the gesture would really catch counts, so a press merely near a part still
 * picks that part.
 */
internal fun pinUnderPress(
    page: Page,
    kind: ShapeKind,
    xPt: Double,
    yPt: Double,
    reachPt: Double,
): CircuitSnapping.PinTarget? = CircuitSnapping.draftTargets(kind, page)
    .firstOrNull { hypot(it.absX - xPt, it.absY - yPt) <= reachPt }

/** Everything a press with [kind] in hand may claim: a living element of its family, or its own figure. */
internal fun pickPlacedElement(page: Page, xPt: Double, yPt: Double, kind: ShapeKind): ElementRef? =
    livingFamilyOf(kind)?.let { pickLivingElement(page, xPt, yPt, kind) }
        ?: pickFigureElement(page, xPt, yPt, kind)

/**
 * A press with a placement tool in hand: **select** the object that tool made under it (and let the same
 * press drag it), or answer false so the press places a new one as it always did.
 *
 * The selection the press lands on keeps its own grips first: the object that was just placed is
 * selected, and its handle is the next thing the hand goes to — refusing that press in favour of another
 * placement would make the handles of a fresh object unreachable without picking Select first.
 */
internal fun DrawingSurfaceView.beginPlacementPress(event: MotionEvent, pointerIndex: Int): Boolean {
    // The **Plot** slot is a placement mode rather than a shape, and it asks the page the same question: a press
    // on a plot it made is the pick (`pickPlotRef`), and paper is where the next graph goes.
    val plotSlot = placeKind == PlaceKind.PLOT
    val kind = if (plotSlot) null else shapeKind ?: return false
    val chosen = selection
    val claimed = when {
        chosen == null -> false
        kind != null -> claims(chosen, kind)
        else -> matchesFamily(chosen, LivingEditFamily.PLOT)
    }
    if (claimed && gestures.pressSelection(event)) return true
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val box = layout.pageAt(x + scrollX, y + scrollY) ?: return false
    val page = pageAt(box.index) ?: return false
    val xPt = box.toPtX(x, scrollX)
    val yPt = box.toPtY(y, scrollY)
    val ref = (if (kind == null) {
        pickPlotRef(page, xPt, yPt)
    } else {
        // A press **on a pin** is where the drawing starts, not a pick of the object the pin belongs to: the
        // press falls through to the stroke, whose draft snaps its first sample onto the mark
        // (`DrawingSurfaceStrokes.snappedShapePoint`), so a wire leaves the terminal. Asked first, because the
        // mark is the finer target — the part behind it is only the ink it was drawn on.
        if (pinUnderPress(page, kind, xPt, yPt, CircuitSnapping.thresholdFor(box.scale)) != null) return false
        pickPlacedElement(page, xPt, yPt, kind)
    }) ?: run {
        // Paper that holds nothing of the slot's own **lets go** of the selection (`letGoOfSelection`).
        letGoOfSelection(chosen)
        return false
    }
    selection = ActiveSelection(box.index, setOf(ref))
    onSelectionChanged?.invoke(true)
    reportSelectionRect()
    // The press that picked the object is the one that may drag it: a tap selects, a drag sizes or
    // carries it, which is the whole point of selecting with the tool still in hand.
    gestures.pressSelection(event)
    render()
    return true
}

/**
 * Pressing paper that holds nothing of the tool's kind **lets go of the selection**, whatever the slot: a figure,
 * a table or a circuit part.
 *
 * Every top-bar element tool stays in hand after its object is placed, so a press on the sheet is how the next
 * one is put down — and that press also kept the old object selected, so a tap meant to let go drew another one
 * and selected it, and the only way to end a selection was to pick another tool. The press now drops the
 * selection and, if it stays a **tap**, writes nothing at all (`ShapeDraft.onlyLetGo`, read by
 * `DrawingSurfaceStrokes.commitCurrent`): tapping empty paper is how the tool is put down, and the next press
 * places. A **drag** is still the drawing it was, so a second object can be laid on the sheet without picking
 * the slot again.
 */
private fun DrawingSurfaceView.letGoOfSelection(chosen: ActiveSelection?) {
    if (chosen == null) return
    clearSelection()
    shapeDraft.letGoNext()
}

/** Whether [sel] is what a press with [kind] in hand would have selected — its family, or its own figure. */
private fun DrawingSurfaceView.claims(sel: ActiveSelection, kind: ShapeKind): Boolean {
    livingFamilyOf(kind)?.let { family -> return matchesFamily(sel, family) }
    if (ShapeBuilder.isMultiStroke(kind)) return false
    val page = pageAt(sel.pageIndex) ?: return false
    val elements = sel.refs.mapNotNull { page.layers.getOrNull(it.layerIndex)?.elements?.getOrNull(it.elementIndex) }
    val only = elements.singleOrNull() as? Stroke ?: return false
    return FigurePins.isFigureOf(only, kind)
}

/** Whether [sel] holds exactly the elements of [family] — a fresh insertion holds one. */
private fun DrawingSurfaceView.matchesFamily(sel: ActiveSelection, family: LivingEditFamily): Boolean {
    val page = pageAt(sel.pageIndex) ?: return false
    val elements = sel.refs.mapNotNull { page.layers.getOrNull(it.layerIndex)?.elements?.getOrNull(it.elementIndex) }
    return elements.isNotEmpty() && elements.size == sel.refs.size && elements.all { family.matches(it) }
}

internal fun shouldClearSelectionOnToolChange(
    wasSelecting: Boolean,
    nowSelecting: Boolean,
    wasShapeKind: ShapeKind?,
    nowShapeKind: ShapeKind?,
    wasPlaceKind: PlaceKind?,
    nowPlaceKind: PlaceKind?,
): Boolean {
    if (wasSelecting && !nowSelecting) return true
    val wasPlacement = wasShapeKind != null || wasPlaceKind != null
    return wasPlacement && (nowShapeKind != wasShapeKind || nowPlaceKind != wasPlaceKind)
}
