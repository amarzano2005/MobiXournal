package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitLabelSide
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A circuit component compiled into ink (`docs/mobix-format.md`, the synthesis export) — and, through `LivingInk`, the drawing on screen.
 *
 * The element stores **placement plus anchors**, not a drawing, so the drawing has to be recovered
 * before it can be written: the symbol's key names its geometry in the shape library
 * ([ShapeBuilder]), and the component's own pins are the only evidence of *where that geometry goes*.
 *
 * The pins are therefore the authority for the drag the library's functions take:
 *
 *  - They are taken **before** the rotation, which is the frame the element states them in, and the
 *    finished geometry is turned afterwards — so a component rotated 90° has its pins exactly where
 *    the element says they are.
 *  - The drag is the **part's own x, through the middle of its pins** ([axisRunsAlongX]) — the line the
 *    factory laid the component out along. A two-terminal part states its two ends there and a gate
 *    states its inputs and its output across it, so the axis through the middle of them is the gate's
 *    own left-to-right drag, never the diagonal between two pins. Reading it as the *longer side of
 *    the pins' box* instead turned a gate a quarter turn the moment a handle made it taller than it is
 *    long, which is a size deciding what the symbol is.
 *  - A **single-pin** symbol (the ground) hangs off that pin: its axis starts at the pin and runs
 *    out along +x for the default size ([DEFAULT_LENGTH_PT]), and the rotation turns the whole
 *    symbol about the pin — so the terminal stays exactly where the element says it is.
 *  - A component with no pins at all has no axis to read, so it is drawn at the same **default
 *    size** about its position: a plain symbol is still a symbol.
 *
 * The axis states the symbol's **one** length; its height is the library's business, derived from
 * that length — unless a handle has given the part a [CircuitElement.height], which is then applied
 * across the axis in the component's own frame. That is what lets a part be stretched wide and short
 * like any other figure, while its pins and its ink stay on the same line.
 *
 * A symbol whose key this build does not have returns null, and the export reports it rather than
 * drawing a substitute.
 */
object CircuitInk {

    /** The length a component with no pins to derive an axis from is drawn at. */
    const val DEFAULT_LENGTH_PT: Double = 48.0

    /** Air between the symbol and its value label, in pt. */
    private const val LABEL_GAP_PT = 3.0

    /** [circuit] as ink, or null when its symbol is not one this build has a drawing for. */
    fun ink(circuit: CircuitElement): List<Element>? {
        val kind = ShapeKind.entries.firstOrNull { it.name == circuit.symbol } ?: return null
        val axis = axisOf(circuit)
        val width = circuit.strokeWidth
        val paths = pathsOf(circuit, kind)

        val out = mutableListOf<Element>()
        for (path in paths) {
            if (path.size < 2) continue
            out += InkPrimitives.stroke(path.map { rotated(it, circuit) }, circuit.strokeColor, width)
        }
        // The label is placed around what was **actually drawn**, so a rotated symbol gets its value
        // above or below the symbol the reader sees rather than beside a box that is no longer there.
        circuit.value?.takeIf { it.isNotBlank() }?.let { value ->
            val (x, y) = labelPoint(circuit, value, drawnBounds(out), axis)
            out += InkPrimitives.text(value, x, y, circuit.strokeColor)
        }
        return out.ifEmpty { null }
    }

    /**
     * The height [circuit] is **drawn** at across its own axis, in points — what a side handle starts
     * its stretch from: the part's own [CircuitElement.height] when a handle has set one, otherwise
     * the size the symbol library derives from the part's length.
     */
    fun drawnAcross(circuit: CircuitElement): Double =
        circuit.height?.takeIf { it > 0.0 } ?: naturalAcross(circuit)

    /**
     * The height the symbol library draws [circuit] at across its axis for its current length, in
     * points — the size a part placed but never stretched wears.
     */
    fun naturalAcross(circuit: CircuitElement): Double {
        val kind = ShapeKind.entries.firstOrNull { it.name == circuit.symbol } ?: return 0.0
        val axis = axisOf(circuit)
        return acrossExtent(axis, builtPaths(circuit, kind, axis))
    }

    /** The box the symbol's strokes occupy, or null when there is nothing drawn yet. */
    private fun drawnBounds(ink: List<Element>): Bounds? {
        var left = Double.MAX_VALUE
        var top = Double.MAX_VALUE
        var right = -Double.MAX_VALUE
        var bottom = -Double.MAX_VALUE
        var any = false
        for (element in ink) {
            if (element !is Stroke) continue
            for (p in element.points) {
                any = true
                if (p.x < left) left = p.x
                if (p.x > right) right = p.x
                if (p.y < top) top = p.y
                if (p.y > bottom) bottom = p.y
            }
        }
        return if (any) Bounds(left, top, right, bottom) else null
    }

    /** The drag a symbol's library function takes, in the component's unrotated frame. */
    private data class Axis(val startX: Double, val startY: Double, val endX: Double, val endY: Double) {
    }

    /**
     * The paths [circuit]'s symbol draws in its own frame, with the height the part stores applied.
     *
     * A symbol drawn in parts (a capacitor's plates, an LED's rays) is written as one stroke per part:
     * joining them into one polyline would draw ink across the gap the part is made of.
     */
    private fun pathsOf(circuit: CircuitElement, kind: ShapeKind): List<List<StrokePoint>> {
        val axis = axisOf(circuit)
        val paths = builtPaths(circuit, kind, axis)
        val wanted = circuit.height?.takeIf { it > 0.0 } ?: return paths
        val natural = acrossExtent(axis, paths)
        if (natural <= 0.0) return paths
        return scaleAcross(axis, paths, wanted / natural)
    }

    /** The paths [circuit]'s symbol library draws for its axis, at the library's own height. */
    private fun builtPaths(circuit: CircuitElement, kind: ShapeKind, axis: Axis): List<List<StrokePoint>> {
        val width = circuit.strokeWidth
        return ShapeBuilder.buildMulti(kind, axis.startX, axis.startY, axis.endX, axis.endY, width)
            ?: listOf(ShapeBuilder.build(kind, axis.startX, axis.startY, axis.endX, axis.endY, width))
    }

    /** How far [paths] reach **across** [axis], in points: the spread of their perpendicular offsets. */
    private fun acrossExtent(axis: Axis, paths: List<List<StrokePoint>>): Double {
        val dx = axis.endX - axis.startX
        val dy = axis.endY - axis.startY
        val len = hypot(dx, dy)
        if (len == 0.0) return 0.0
        var low = Double.MAX_VALUE
        var high = -Double.MAX_VALUE
        for (path in paths) {
            for (p in path) {
                // The signed distance off the axis line, before the rotation the element states.
                val off = ((p.x - axis.startX) * dy - (p.y - axis.startY) * dx) / len
                if (off < low) low = off
                if (off > high) high = off
            }
        }
        return if (low <= high) high - low else 0.0
    }

    /**
     * [paths] with every point's distance **across** [axis] multiplied by [factor] — the axis line it
     * hangs off stays exactly where the element put it, so the leads keep meeting their pins.
     */
    private fun scaleAcross(axis: Axis, paths: List<List<StrokePoint>>, factor: Double): List<List<StrokePoint>> {
        val dx = axis.endX - axis.startX
        val dy = axis.endY - axis.startY
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return paths
        return paths.map { path ->
            path.map { p ->
                val along = ((p.x - axis.startX) * dx + (p.y - axis.startY) * dy) / len2
                val bx = axis.startX + along * dx
                val by = axis.startY + along * dy
                p.copy(x = bx + (p.x - bx) * factor, y = by + (p.y - by) * factor)
            }
        }
    }

    /**
     * Whether [circuit]'s axis runs along its **own x** — the line its pins state, before the rotation.
     *
     * The factory lays every component out along x ([CircuitElementFactory.defaultPinsFor]), so a part
     * whose pins spread on x at all is an x-axis part, and only pins stated on y alone (a part written
     * on its side) put the axis on y. This is a property of how the part was drawn, so no size can
     * change it: reading the **longer side of the pins' box** was stable for a two-terminal part, whose
     * pins never leave the axis, but a gate states its inputs on both axes — stretched across far
     * enough, its box became taller than long and the whole symbol was redrawn a quarter turn
     * (`CircuitResizeTest`, `SelectionOps.circuitStretch`, which reads the same answer).
     */
    fun axisRunsAlongX(circuit: CircuitElement): Boolean {
        if (circuit.pins.size < 2) return true
        return circuit.pins.maxOf { it.relX } - circuit.pins.minOf { it.relX } > 0.0
    }

    private fun axisOf(circuit: CircuitElement): Axis {
        if (circuit.pins.size == 1) {
            val pin = circuit.pins.first()
            return Axis(
                circuit.positionX + pin.relX, circuit.positionY + pin.relY,
                circuit.positionX + pin.relX + DEFAULT_LENGTH_PT, circuit.positionY + pin.relY,
            )
        }
        if (circuit.pins.isEmpty()) {
            val half = DEFAULT_LENGTH_PT / 2.0
            return Axis(
                circuit.positionX - half, circuit.positionY,
                circuit.positionX + half, circuit.positionY,
            )
        }
        val xs = circuit.pins.map { circuit.positionX + it.relX }
        val ys = circuit.pins.map { circuit.positionY + it.relY }
        val left = xs.min(); val right = xs.max()
        val top = ys.min(); val bottom = ys.max()
        return if (axisRunsAlongX(circuit)) {
            val middle = (top + bottom) / 2.0
            Axis(left, middle, right, middle)
        } else {
            val middle = (left + right) / 2.0
            Axis(middle, top, middle, bottom)
        }
    }

    /** [point] turned by the component's rotation about its position — pins and ink turn together. */
    private fun rotated(point: StrokePoint, circuit: CircuitElement): StrokePoint {
        if (circuit.rotation == 0.0) return point
        val radians = Math.toRadians(circuit.rotation)
        val dx = point.x - circuit.positionX
        val dy = point.y - circuit.positionY
        val cos = cos(radians)
        val sin = sin(radians)
        return StrokePoint(
            circuit.positionX + dx * cos - dy * sin,
            circuit.positionY + dx * sin + dy * cos,
            point.width,
        )
    }

    /**
     * Where the value label goes: **on the chosen side of the drawn symbol**, never on top of it
     * ([CircuitElement.labelSide]). Above and below are centred horizontally on the symbol; left and
     * right are centred vertically on it, which is what a turned part with wired leads wants.
     *
     * It used to be dropped a dozen points off the axis' middle, with the text's top-left corner on
     * the midpoint — which put the value across the symbol as soon as a component was rotated,
     * because the ink turns and the label does not.
     *
     * The label is *not* rotated with the component, even so: a value is read, not turned. That is why
     * the side is measured on the turned symbol's own box, in page space.
     */
    private fun labelPoint(
        circuit: CircuitElement,
        value: String,
        drawn: Bounds?,
        axis: Axis,
    ): Pair<Double, Double> {
        val size = InkTokens.FIGURE_LABEL_SIZE_PT
        val labelWidth = TextAdvance.pt(value, size)
        val labelHeight = size * InkTokens.FIGURE_LABEL_LINE_H
        // A symbol with no geometry this build can draw still has an axis: laying the value out
        // around that degenerate box keeps every side meaning the same thing.
        val box = drawn ?: Bounds(
            minOf(axis.startX, axis.endX), minOf(axis.startY, axis.endY),
            maxOf(axis.startX, axis.endX), maxOf(axis.startY, axis.endY),
        )
        val x = when (circuit.labelSide) {
            CircuitLabelSide.ABOVE, CircuitLabelSide.BELOW -> (box.left + box.right) / 2.0 - labelWidth / 2.0
            CircuitLabelSide.LEFT -> box.left - LABEL_GAP_PT - labelWidth
            CircuitLabelSide.RIGHT -> box.right + LABEL_GAP_PT
        }
        val y = when (circuit.labelSide) {
            CircuitLabelSide.ABOVE -> box.top - LABEL_GAP_PT - labelHeight
            CircuitLabelSide.BELOW -> box.bottom + LABEL_GAP_PT
            CircuitLabelSide.LEFT, CircuitLabelSide.RIGHT -> (box.top + box.bottom) / 2.0 - labelHeight / 2.0
        }
        return x to y
    }
}
