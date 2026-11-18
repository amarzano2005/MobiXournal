/**
 * What the top bar's **element** tools do with a press: an object of the tool's own family under the
 * point is *selected*, and anything else takes a new one.
 *
 * An element tool (Table, a passive part, a logic gate) is a *placement* mode, so before this rule
 * every press reserved a new object — a tap on a resistor with the resistor tool aimed to size it
 * placed a second one on top instead. The rule is the one a drawing tool cannot express: the point
 * decides. A press on a table with the Table tool selects that table, a press on paper places one, and
 * the same press that selected an object is handed to the selection so its handles can be dragged at
 * once — which is what makes an element **inserted and then sized** one gesture and not three.
 *
 * The tool stays in hand across the insertion ([com.mobixournal.ui.toolAfterInsertion] answers null
 * for these tools), so a row of parts is placed without picking the slot again.
 *
 * The family is [LivingEditFamily], the same vocabulary the double-tap uses: "the same typology" is
 * the model's own idea of a table, a circuit, a plot, a figure or a formula, never a second list here.
 * Pure page geometry, so the pick is unit-tested on the JVM (`LivingPlacementPickTest`).
 */
package com.mobixournal.render

import android.view.MotionEvent
import com.mobixournal.format.model.Page

/**
 * The family a top-bar **element** tool places, or null for a tool that draws ink instead.
 *
 * The shape kinds that put a *living* object on the page are exactly the table and the circuit symbols
 * ([CircuitElementFactory.isCircuitKind]); every other shape tool — a line, a rectangle, a bond — draws
 * a stroke and has no family to select.
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
 * A press with an element tool in hand: **select** the object of that tool's family under it (and let
 * the same press drag it), or answer false so the press places a new one as it always did.
 *
 * The selection the press lands on keeps its own grips first: the object that was just placed is
 * selected, and its handle is the next thing the hand goes to — refusing that press in favour of
 * another placement would make the handles of a fresh part unreachable without picking Select first.
 */
internal fun DrawingSurfaceView.beginLivingToolPress(event: MotionEvent, pointerIndex: Int): Boolean {
    val kind = shapeKind ?: return false
    val family = livingFamilyOf(kind) ?: return false
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val chosen = selection?.takeIf { matchesFamily(it, family) }
    if (chosen != null && gestures.pressSelection(event)) return true
    val box = layout.pageAt(x + scrollX, y + scrollY) ?: return false
    val page = pageAt(box.index) ?: return false
    val ref = pickLivingElement(page, box.toPtX(x, scrollX), box.toPtY(y, scrollY), kind) ?: return false
    selection = ActiveSelection(box.index, setOf(ref))
    onSelectionChanged?.invoke(true)
    reportSelectionRect()
    // The press that picked the object is the one that may drag it: a tap selects, a drag sizes or
    // carries it, which is the whole point of selecting with the tool still in hand.
    gestures.pressSelection(event)
    render()
    return true
}

/** Whether [sel] holds exactly the elements of [family] — a fresh insertion holds one. */
private fun DrawingSurfaceView.matchesFamily(sel: ActiveSelection, family: LivingEditFamily): Boolean {
    val page = pageAt(sel.pageIndex) ?: return false
    val elements = sel.refs.mapNotNull { page.layers.getOrNull(it.layerIndex)?.elements?.getOrNull(it.elementIndex) }
    return elements.isNotEmpty() && elements.size == sel.refs.size && elements.all { family.matches(it) }
}

