package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Stroke
import kotlin.math.atan2
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
 * A free drag of a shaft's end: a new length **and** a new direction, both read from the pointer.
 *
 * [factor] scales the shaft about the anchored end and [angle] turns it, which is one similarity transform —
 * see `SelectionEdit.similarity`. Neither changes the pen's width.
 */
data class ShaftDrag(val factor: Double, val angle: Double)

/**
 * The drag a pointer at ([x], [y]) describes while ([anchorX], [anchorY]) stays still, given where the
 * pointer grabbed the end ([grabX], [grabY]).
 *
 * The grabbed end follows the hand **wherever it goes**, so a drag across the shaft re-aims it and a drag
 * along it lengthens it: the two freedoms a line or an arrow has, in one grip.
 */
fun shaftDrag(
    grabX: Double,
    grabY: Double,
    x: Double,
    y: Double,
    anchorX: Double,
    anchorY: Double,
): ShaftDrag {
    val grabDx = grabX - anchorX
    val grabDy = grabY - anchorY
    val start = hypot(grabDx, grabDy).coerceAtLeast(1e-3)
    val dx = x - anchorX
    val dy = y - anchorY
    return ShaftDrag(
        factor = hypot(dx, dy) / start,
        angle = atan2(grabDx * dy - grabDy * dx, grabDx * dx + grabDy * dy),
    )
}

/**
 * A shaft's **turn control**: its middle is the pivot, and the ring sits [gapPt] out from it on the side that
 * is upward on the page.
 *
 * A line and an arrow have no box for a ring to float above, so the ring is measured from the ink itself —
 * the stem starts on the shaft and the knob is a stem's length away, whichever way the shaft points.
 */
data class ShaftTurn(val pivotX: Double, val pivotY: Double, val ringX: Double, val ringY: Double)

/** Where a shaft's turn ring sits — see [ShaftTurn]. */
fun shaftTurn(shaft: SelectionShaft, gapPt: Double): ShaftTurn {
    val pivotX = (shaft.tailX + shaft.tipX) / 2
    val pivotY = (shaft.tailY + shaft.tipY) / 2
    val length = shaft.length.coerceAtLeast(1e-6)
    val normalX = -(shaft.tipY - shaft.tailY) / length
    val normalY = (shaft.tipX - shaft.tailX) / length
    // The page's y grows downward, so a normal with a positive y points **down**: flip it to sit above.
    val side = if (normalY <= 0.0) 1.0 else -1.0
    return ShaftTurn(pivotX, pivotY, pivotX + normalX * gapPt * side, pivotY + normalY * gapPt * side)
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
