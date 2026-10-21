package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement

/**
 * An axis-aligned bounding box in page-local pt space (top-left origin, y down). Used by the
 * selection tool to hit-test taps, test rubber-band containment, and draw the selection outline.
 * Kept free of Android types so it's unit-testable on the JVM.
 */
data class Bounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    /** Width in points (right - left). */
    val width: Double get() = right - left
    /** Height in points (bottom - top). */
    val height: Double get() = bottom - top

    /** True if (x, y) lies within this box (inclusive of the edges). */
    fun contains(x: Double, y: Double): Boolean = x in left..right && y in top..bottom

    /** True if this box lies wholly inside [outer] (used for rectangle-select containment). */
    fun containedBy(outer: Bounds): Boolean =
        left >= outer.left && top >= outer.top && right <= outer.right && bottom <= outer.bottom

    /**
     * This box grown by [pad] pt on every side (a hit-test / handle margin).
     * @param pad Padding in points to expand on each side.
     */
    fun expand(pad: Double): Bounds = Bounds(left - pad, top - pad, right + pad, bottom + pad)

    /**
     * This box shifted by (dx, dy) pt.
     * @param dx Horizontal translation in points.
     * @param dy Vertical translation in points.
     */
    fun translate(dx: Double, dy: Double): Bounds = Bounds(left + dx, top + dy, right + dx, bottom + dy)

    /** True if this box overlaps [other] at all (edge contact counts). */
    fun intersects(other: Bounds): Boolean =
        left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

    /** The smallest box covering both this and [other]. */
    fun union(other: Bounds): Bounds =
        Bounds(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
}

/**
 * Computes the page-local pt bounding box of any [Element]. Image / LaTeX boxes are explicit;
 * a stroke's box is the extent of its vertices grown by each vertex's half-width; a text box is
 * approximated from its content extent (the same rough metric [DrawingSurfaceView] uses to hit
 * text) since glyphs aren't measured off-device.
 */
object ElementBounds {

    /** Rough per-em width factor and line-height factor for the text-box approximation. */
    private const val TEXT_CHAR_W = 0.62
    private const val TEXT_LINE_H = 1.3

    /**
     * Extra pt margin around an element's box when tap-testing, so thin strokes and empty text
     * boxes stay pickable. Shared by every hit test ([SelectionTester], [ElementEdits]) so a tap
     * picks the same element whichever path handles it.
     */
    const val TAP_PAD = 4.0

    /**
     * True when [element] has geometry we can trust enough to hit-test. Unmodelled [RawElement]s
     * and empty strokes have none: [of] gives them an empty box at the page origin, which a
     * containment test would happily read as a real box sitting in the top-left corner and select.
     * Every hit test ([SelectionTester], [ElementEdits]) filters on this first rather than relying
     * on the empty box to exclude them.
     */
    fun isHitTestable(element: Element): Boolean = when (element) {
        is RawElement -> false
        // Plot, Table and Circuit are living entities with selection and move support (`docs/mobix-elements.md`).
        // The remaining one (parametricShape) keeps an empty box and is not hit-testable until its editor lands.
        is PlotElement, is TableElement, is CircuitElement -> true
        is ParametricShapeElement -> false
        is Stroke -> element.points.isNotEmpty()
        else -> true
    }

    /**
     * Compute the bounding box of [element] in page-local points.
     * @return Bounds in pt space, or empty box (0,0,0,0) for unmodelled elements.
     */
    fun of(element: Element): Bounds = when (element) {
        is Stroke -> strokeBounds(element)
        is TextElement -> textBounds(element)
        is ImageElement -> Bounds(element.left, element.top, element.right, element.bottom)
        is TexImageElement -> Bounds(element.left, element.top, element.right, element.bottom)
        is PlotElement -> plotBounds(element)
        is TableElement -> tableBounds(element)
        is CircuitElement -> circuitBounds(element)
        // An element we don't model has no geometry we can trust; the empty box keeps it out of
        // every hit test, the same answer an empty stroke gets.
        is RawElement -> Bounds(0.0, 0.0, 0.0, 0.0)
        // The other living entity (parametricShape) carries its own geometry and is
        // drawn through it, but its box stays deliberately empty until its editor lands (`docs/mobix-elements.md`).
        is ParametricShapeElement -> Bounds(0.0, 0.0, 0.0, 0.0)
    }

    private fun tableBounds(t: TableElement): Bounds {
        val half = t.borderWidth / 2.0
        return Bounds(t.left - half, t.top - half, t.left + t.width + half, t.top + t.height + half)
    }

    private fun plotBounds(p: PlotElement): Bounds {
        val strokeW = p.curves.firstOrNull()?.strokeWidth ?: 1.0
        val half = strokeW / 2.0
        return Bounds(p.left - half, p.top - half, p.right + half, p.bottom + half)
    }

    private fun strokeBounds(s: Stroke): Bounds {
        val pts = s.points
        if (pts.isEmpty()) return Bounds(0.0, 0.0, 0.0, 0.0)
        var left = Double.MAX_VALUE
        var top = Double.MAX_VALUE
        var right = -Double.MAX_VALUE
        var bottom = -Double.MAX_VALUE
        for (p in pts) {
            val half = p.width / 2.0
            if (p.x - half < left) left = p.x - half
            if (p.y - half < top) top = p.y - half
            if (p.x + half > right) right = p.x + half
            if (p.y + half > bottom) bottom = p.y + half
        }
        return Bounds(left, top, right, bottom)
    }

    private fun textBounds(t: TextElement): Bounds {
        val lines = t.content.split("\n")
        val h = lines.size * t.size * TEXT_LINE_H
        val w = (lines.maxOfOrNull { it.length } ?: 1) * t.size * TEXT_CHAR_W
        return Bounds(t.x, t.y, t.x + w, t.y + h)
    }

    private fun circuitBounds(c: CircuitElement): Bounds {
        val ink = CircuitInk.ink(c)
        if (ink.isNullOrEmpty()) {
            val half = CircuitInk.DEFAULT_LENGTH_PT / 2.0
            return Bounds(c.positionX - half, c.positionY - half, c.positionX + half, c.positionY + half)
        }
        var left = Double.MAX_VALUE
        var top = Double.MAX_VALUE
        var right = -Double.MAX_VALUE
        var bottom = -Double.MAX_VALUE
        for (el in ink) {
            when (el) {
                is Stroke -> {
                    val sb = strokeBounds(el)
                    if (sb.left < left) left = sb.left
                    if (sb.top < top) top = sb.top
                    if (sb.right > right) right = sb.right
                    if (sb.bottom > bottom) bottom = sb.bottom
                }
                is TextElement -> {
                    val tb = textBounds(el)
                    if (tb.left < left) left = tb.left
                    if (tb.top < top) top = tb.top
                    if (tb.right > right) right = tb.right
                    if (tb.bottom > bottom) bottom = tb.bottom
                }
                else -> Unit
            }
        }
        return if (left <= right && top <= bottom) Bounds(left, top, right, bottom)
        else Bounds(c.positionX, c.positionY, c.positionX, c.positionY)
    }
}
