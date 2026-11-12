package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Stroke
import kotlin.math.hypot

/**
 * A **line or an arrow**: a 2-D selection with **one** degree of freedom — the length of its shaft.
 *
 * The box of a diagonal line, or of any arrow, has a width and a height of its own, so a handle rule that
 * reads the box alone treats both as ordinary figures and offers them eight handles. They are not figures:
 * the ink has its own axis, and a stretch across that axis can only bend the drawing or flatten the head.
 * A shaft names that axis by its two ends — the line's two ends, the arrow's tail and tip — so a shell can
 * hang a handle on each and change nothing but the distance between them.
 */
data class SelectionShaft(
    val tailX: Double,
    val tailY: Double,
    val tipX: Double,
    val tipY: Double,
) {
    /** How long the shaft is, in page pt — the one number a drag of its ends changes. */
    val length: Double get() = hypot(tipX - tailX, tipY - tailY)

    /**
     * The uniform factor a pointer at ([x], [y]) asks for while ([anchorX], [anchorY]) stays still: the
     * distance from the anchored end now, over that distance at grab time. A purely uniform factor keeps
     * the shaft's direction and the glyph's shape — only the length changes — and it clamps at the caller,
     * which owns the resize limits.
     */
    fun factorAt(x: Double, y: Double, anchorX: Double, anchorY: Double, startDist: Double): Double =
        hypot(x - anchorX, y - anchorY) / startDist.coerceAtLeast(1e-3)
}

/**
 * The shaft of a single **line or arrow** element, or null for anything else — a closed figure, a component,
 * an image, an empty stroke.
 *
 * [FigurePins] already knows where a connector's two ends are (`ends`), so this reads them rather than
 * measuring the ink a second time: a straight polyline's ends are its extremes, and an arrow's are its tail
 * and the tip of its head.
 */
fun shaftOf(element: Element?): SelectionShaft? {
    val stroke = element as? Stroke ?: return null
    when (FigurePins.familyOf(stroke)) {
        PinFamily.WIRE, PinFamily.ARROW -> Unit
        else -> return null
    }
    val ends = FigurePins.ends(stroke.points)
    val tail = ends.firstOrNull() ?: return null
    val tip = ends.lastOrNull() ?: return null
    if (tail.first == tip.first && tail.second == tip.second) return null
    return SelectionShaft(tail.first, tail.second, tip.first, tip.second)
}
