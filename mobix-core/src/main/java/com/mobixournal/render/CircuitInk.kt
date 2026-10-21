package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
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

    /** How far the value label sits off the component's axis. */
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
        circuit.value?.takeIf { it.isNotBlank() }?.let { value ->
            val (x, y) = labelPoint(circuit, axis)
            out += InkPrimitives.text(value, x, y, circuit.strokeColor)
        }
        return out.ifEmpty { null }
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
     * Where the value label goes: off the middle of the axis, on the side a dimension is written —
     * above a horizontal axis, to the right of a vertical one. (The label is *not* rotated with the
     * component: a value is read, not turned.)
     */
    private fun labelPoint(circuit: CircuitElement, axis: Axis): Pair<Double, Double> {
        val x = if (axis.horizontal) axis.midX else axis.midX + VALUE_OFFSET_PT
        val y = if (axis.horizontal) axis.midY - VALUE_OFFSET_PT else axis.midY
        return x to y
    }
}
