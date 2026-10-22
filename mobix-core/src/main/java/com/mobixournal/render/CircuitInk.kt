package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import kotlin.math.abs
import kotlin.math.cos
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
 *  - The drag is the **longer axis of the pins' box, through its middle**. A two-terminal part states
 *    its two ends and the axis falls out of them; a gate states an input and an output on each side,
 *    and the axis through the middle of them is the gate's own left-to-right drag — not the diagonal
 *    between two pins, which would draw the symbol crooked.
 *  - A **single-pin** symbol (the ground) hangs off that pin: its axis starts at the pin and runs
 *    out along +x for the default size ([DEFAULT_LENGTH_PT]), and the rotation turns the whole
 *    symbol about the pin — so the terminal stays exactly where the element says it is.
 *  - A component with no pins at all has no axis to read, so it is drawn at the same **default
 *    size** about its position: a plain symbol is still a symbol.
 *
 * A symbol whose key this build does not have returns null, and the export reports it rather than
 * drawing a substitute.
 */
object CircuitInk {

    /** The length a component with no pins to derive an axis from is drawn at. */
    const val DEFAULT_LENGTH_PT: Double = 48.0

    /** Air between the symbol and its value label, in pt. */
    private const val LABEL_GAP_PT = 3.0

    /** How far the value label sits off the component's axis when there is no symbol to clear. */
    private const val VALUE_OFFSET_PT = 12.0

    /** [circuit] as ink, or null when its symbol is not one this build has a drawing for. */
    fun ink(circuit: CircuitElement): List<Element>? {
        val kind = ShapeKind.entries.firstOrNull { it.name == circuit.symbol } ?: return null
        val axis = axisOf(circuit)
        val width = circuit.strokeWidth
        // A symbol drawn in parts (a capacitor's plates, an LED's rays) is written as one stroke per
        // part: joining them into one polyline would draw ink across the gap the part is made of.
        val paths = ShapeBuilder.buildMulti(kind, axis.startX, axis.startY, axis.endX, axis.endY, width)
            ?: listOf(ShapeBuilder.build(kind, axis.startX, axis.startY, axis.endX, axis.endY, width))

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
        val midX: Double get() = (startX + endX) / 2.0
        val midY: Double get() = (startY + endY) / 2.0

        /** Whether the axis runs left to right, which is what decides where a label goes. */
        val horizontal: Boolean get() = abs(endX - startX) >= abs(endY - startY)
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
        return if (right - left >= bottom - top) {
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
     * Where the value label goes: **above or below the drawn symbol**, centred on it, never on top of
     * it ([CircuitElement.labelBelow]). It used to be dropped a dozen points off the axis' middle,
     * with the text's top-left corner on the midpoint — which put the value across the symbol as soon
     * as a component was rotated, because the ink turns and the label does not.
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
        val labelWidth = value.length * size * InkTokens.FIGURE_LABEL_CHAR_W
        val labelHeight = size * InkTokens.FIGURE_LABEL_LINE_H
        if (drawn == null) {
            // A symbol with no geometry this build can draw: fall back to the axis, so the value is
            // still somewhere sensible beside where the component sits.
            val x = if (axis.horizontal) axis.midX else axis.midX + VALUE_OFFSET_PT
            val y = if (axis.horizontal) axis.midY - VALUE_OFFSET_PT else axis.midY
            return x to y
        }
        val x = (drawn.left + drawn.right) / 2.0 - labelWidth / 2.0
        val y = if (circuit.labelBelow) {
            drawn.bottom + LABEL_GAP_PT
        } else {
            drawn.top - LABEL_GAP_PT - labelHeight
        }
        return x to y
    }
}
