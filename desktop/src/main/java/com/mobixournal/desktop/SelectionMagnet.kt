package com.mobixournal.desktop

import com.mobixournal.format.model.Page
import com.mobixournal.render.CircuitSnapping
import com.mobixournal.render.PageSelection

/**
 * The delta a **moved selection** actually travels, with its pins magnetised.
 *
 * A circuit is assembled in two ways: a part is drawn next to the one it feeds, or a part is placed and
 * then **dragged** into place. The first has been snapped since the drag existed; the second was not, so a
 * component selected after its insertion could only ever be moved by eye and never joined — the pins it
 * had just minted were targets for everyone but itself.
 *
 * The rule is the shared one (`CircuitSnapping.snapTranslation`), so a moved part and a drawn one land on
 * a pin by the same reach: components and wires are the only things that magnetise, and a figure crossing
 * the sheet keeps the hand's own translation. [scale] is the view's points-to-pixels factor, which is what
 * turns the reach into something a person can aim with.
 *
 * Pure, so the magnet is a JVM test rather than something to feel for with a pen.
 */
internal fun snapSelectionMove(
    page: Page,
    selection: PageSelection,
    dx: Double,
    dy: Double,
    scale: Float,
): Pair<Double, Double> = CircuitSnapping.snapTranslation(
    page = page,
    refs = selection.refs,
    rawDx = dx,
    rawDy = dy,
    thresholdPt = CircuitSnapping.thresholdFor(scale),
)
