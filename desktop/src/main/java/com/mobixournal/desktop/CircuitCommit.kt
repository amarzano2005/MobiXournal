package com.mobixournal.desktop

import com.mobixournal.render.ShapeKind

/**
 * The **circuit** slot's placement: the part a drag pulled out, between the two page points it spans,
 * already snapped to the pins in reach ([CircuitGesture]).
 *
 * A component's palette question is answered *before* the drag — the family's picker was the question — so
 * nothing is asked here: the element is written at once, as one edit and one undo step, and the window
 * hands the page to the selection so the next press can move or edit what was placed.
 *
 * It lives beside the drag rather than inside `DesktopShellState` because the shell is the window's wiring
 * and is already at its line budget, and the two halves of a circuit placement read better together
 * (`SourceStructureTest`).
 */
internal fun DesktopShellState.placeCircuit(tab: DesktopTab, kind: ShapeKind, span: CircuitSpan) {
    val element = circuitElementForSpan(kind, span, shapeInk) ?: return
    if (placeLivingElement(tab, element) { index -> edited(tab, index) }) handToSelection()
}
