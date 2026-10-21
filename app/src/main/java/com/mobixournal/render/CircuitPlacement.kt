/**
 * Placing a circuit component: what a finished **drag** becomes in the document.
 *
 * The magnet that aims that drag lives in [CircuitSnapping] (pure, and unit-tested); this is the one
 * step that touches the document, kept apart from the ink-capture file so that file stays about ink.
 * The geometry it saves is the geometry the drag previewed — the endpoints were already snapped to a
 * pin or to a wire's end while the stylus was down ([DrawingSurfaceView.snappedPoint]).
 */
package com.mobixournal.render

/**
 * Author the component the current drag describes, on the current page, and make it undoable like any
 * other element edit.
 */
internal fun DrawingSurfaceView.commitCircuitInsertion(kind: ShapeKind) {
    val circuit = CircuitElementFactory.circuitElementFor(
        symbol = kind.name,
        startX = shapeStartX,
        startY = shapeStartY,
        endX = shapeEndX,
        endY = shapeEndY,
        strokeColor = strokeColor(),
        strokeWidth = shapeWidthPt,
    ) ?: return
    ElementEdits.addElement(doc, currentPage, circuit) { resolvedActiveLayer(it) }
        ?.let { commitElementEdit(it) }
    render()
}
