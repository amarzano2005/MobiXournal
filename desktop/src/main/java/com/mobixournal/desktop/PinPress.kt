package com.mobixournal.desktop

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Page
import com.mobixournal.render.CircuitElementFactory
import com.mobixournal.render.CircuitSnapping
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionTester
import com.mobixournal.render.ShapeKind
import kotlin.math.hypot

/**
 * The **pin** a press caught, when one is in reach: the circuit element that press would join.
 *
 * A pin sits at a part's very end, so a press aimed at one lands on the part's edge and often a few points
 * *outside* its drawn ink. The element picker only answers for ink, which meant a press on a pin traced a
 * new marquee instead of picking the part up — and a part that cannot be picked up by its pin is a part
 * whose pins never attach, however well the magnet works once it is moving.
 *
 * Topmost first, so a pin lying under another part still picks the one the eye is on. [radiusPt] is the
 * magnet's own reach (`CircuitSnapping.thresholdFor`), so what a press can catch and what a drag can pull
 * together are the same distance on screen.
 */
internal fun pinnedElementAt(page: Page, x: Double, y: Double, radiusPt: Double): ElementRef? {
    if (radiusPt <= 0.0) return null
    for (layerIndex in page.layers.indices.reversed()) {
        val elements = page.layers[layerIndex].elements
        for (elementIndex in elements.indices.reversed()) {
            val part = elements[elementIndex] as? CircuitElement ?: continue
            for (pin in part.pins) {
                val (px, py) = CircuitSnapping.pinAbsolutePosition(part, pin)
                if (hypot(px - x, py - y) <= radiusPt) return ElementRef(layerIndex, elementIndex)
            }
        }
    }
    return null
}

/**
 * The element a selection press catches: the ink under the pointer, or — when the pointer is on a **pin**
 * rather than on ink — the part that pin belongs to.
 *
 * One call, because the two answers have to be asked in that order: a part lying over another part is
 * picked as ink first, and only empty paper near a pin falls through to the magnet's reach.
 */
internal fun pickForDrag(page: Page, x: Double, y: Double, radiusPt: Double): ElementRef? =
    SelectionTester.pickTopmost(page, x, y) ?: pinnedElementAt(page, x, y, radiusPt)

/**
 * The connector kind a selection stands for, so the sheet keeps marking the pins of a part that was picked
 * **after** it was placed.
 *
 * A circuit slot marks the pins it could join while it is armed (`connectorKindFor`); once the part is
 * written and the window hands the page to the selection, that slot is gone — and the pins vanished with
 * it, exactly when the user wants to drag the part onto them. The selection's one element answers instead.
 */
internal fun selectionConnectorKind(page: Page, selection: PageSelection?): ShapeKind? {
    val ref = selection?.takeIf { it.refs.size == 1 }?.refs?.first() ?: return null
    val part = page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? CircuitElement
        ?: return null
    return ShapeKind.entries.firstOrNull { it.name == part.symbol }
        ?.takeIf { CircuitElementFactory.isConnectorKind(it) }
}
