package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.ElementId
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Factory for authoring living [CircuitElement]s from user canvas interactions.
 *
 * Translates start and end coordinates into a centered [CircuitElement] with
 * canonical pin anchors, rotation in degrees, and minted identity.
 */
object CircuitElementFactory {

    /** Default width of a component inserted via a single tap. */
    const val DEFAULT_LENGTH_PT: Double = 48.0

    /** The set of circuit kinds supported by the symbol library. */
    val CIRCUIT_KINDS: Set<ShapeKind> = setOf(
        ShapeKind.RESISTOR,
        ShapeKind.CAPACITOR,
        ShapeKind.INDUCTOR,
        ShapeKind.GROUND,
        ShapeKind.DIODE,
        ShapeKind.LED,
        ShapeKind.ZENER_DIODE,
        ShapeKind.OPAMP,
        ShapeKind.BJT_NPN,
        ShapeKind.BJT_PNP,
        ShapeKind.DC_SOURCE,
        ShapeKind.CURRENT_SOURCE,
        ShapeKind.AND_GATE,
        ShapeKind.OR_GATE,
        ShapeKind.NOT_GATE,
        ShapeKind.NAND_GATE,
        ShapeKind.NOR_GATE,
        ShapeKind.XOR_GATE,
        ShapeKind.XNOR_GATE,
        ShapeKind.BUFFER_GATE,
        ShapeKind.SWITCH_OPEN,
        ShapeKind.SWITCH_CLOSED,
        ShapeKind.TRANSFORMER,
        ShapeKind.JUNCTION,
    )

    /** Whether [kind] is an electronic circuit or logic gate component. */
    fun isCircuitKind(kind: ShapeKind): Boolean = kind in CIRCUIT_KINDS

    /**
     * Whether [kind] is drawn as a **connector**, the only gestures the magnetic pin snap serves:
     * a circuit component (whose pins must meet a wire) or a wire (the line tool). Everything else
     * — a rectangle, a chemistry bond, a recognised freehand figure — is free ink and must never be
     * pulled off the user's hand onto a nearby pin.
     */
    fun isConnectorKind(kind: ShapeKind): Boolean = kind == ShapeKind.LINE || isCircuitKind(kind)

    /**
     * Emits canonical pin anchors in unrotated page coordinates relative to the component center.
     */
    fun defaultPinsFor(symbol: String, lengthPt: Double): List<CircuitPin> {
        val kind = ShapeKind.entries.firstOrNull { it.name == symbol } ?: return emptyList()
        val half = lengthPt / 2.0
        return when (kind) {
            ShapeKind.AND_GATE, ShapeKind.OR_GATE, ShapeKind.NAND_GATE,
            ShapeKind.NOR_GATE, ShapeKind.XOR_GATE, ShapeKind.XNOR_GATE -> {
                val h = minOf(20.0, lengthPt * 0.30)
                val inSep = h * 0.50
                listOf(
                    CircuitPin("in1", -half, -inSep),
                    CircuitPin("in2", -half, inSep),
                    CircuitPin("out", half, 0.0),
                )
            }
            ShapeKind.OPAMP -> {
                val h = minOf(22.0, lengthPt * 0.32)
                val inSep = h * 0.50
                listOf(
                    CircuitPin("in_inv", -half, -inSep),
                    CircuitPin("in_noninv", -half, inSep),
                    CircuitPin("out", half, 0.0),
                )
            }
            ShapeKind.BJT_NPN, ShapeKind.BJT_PNP -> {
                val h = minOf(20.0, lengthPt * 0.25)
                listOf(
                    CircuitPin("base", -half, 0.0),
                    CircuitPin("collector", half, -h),
                    CircuitPin("emitter", half, h),
                )
            }
            ShapeKind.NOT_GATE, ShapeKind.BUFFER_GATE -> {
                listOf(
                    CircuitPin("in", -half, 0.0),
                    CircuitPin("out", half, 0.0),
                )
            }
            // Ground states its two ends like any other part — the terminal the wire meets (p1) and
            // the far end of its plates (p2) — because the drag is what sizes it. Drawn from p1 to
            // p2 (see [CircuitShapes.ground]): a short drag is a small earth, a long one a long lead
            // ending in the same symbol, and both anchors sit on the same node.
            ShapeKind.GROUND -> {
                listOf(
                    CircuitPin("p1", -half, 0.0),
                    CircuitPin("p2", half, 0.0),
                )
            }
            else -> {
                // Two-terminal passives, sources, switches, transformers, junctions
                listOf(
                    CircuitPin("p1", -half, 0.0),
                    CircuitPin("p2", half, 0.0),
                )
            }
        }
    }

    /**
     * Authors a living [CircuitElement] spanning from ([startX], [startY]) to ([endX], [endY]).
     * Returns null if [symbol] is not recognized in the library.
     */
    fun circuitElementFor(
        symbol: String,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        strokeColor: Int = CircuitElement.DEFAULT_INK,
        strokeWidth: Double = 1.0,
        value: String? = null,
    ): CircuitElement? = placedElementFor(symbol, startX, startY, endX, endY, strokeColor, strokeWidth, value)
        ?.copy(id = ElementId.mint(ElementId.CIRCUIT))

    /**
     * The same geometry as [circuitElementFor] with **no identity** — what the drag in progress is
     * about to become.
     *
     * The live preview asks it for the pins a component would have at the current pointer, so the
     * rings the user lines up on screen are the pins the commit actually mints: one call, one
     * spelling of "a tap is the default length, a drag spans the two ends". Minting an id per frame
     * would be pointless garbage, which is why this half carries none.
     */
    fun placedElementFor(
        symbol: String,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        strokeColor: Int = CircuitElement.DEFAULT_INK,
        strokeWidth: Double = 1.0,
        value: String? = null,
    ): CircuitElement? {
        val kind = ShapeKind.entries.firstOrNull { it.name == symbol } ?: return null
        if (!isCircuitKind(kind)) return null

        val dx = endX - startX
        val dy = endY - startY
        val rawLen = hypot(dx, dy)
        val isTap = rawLen < 6.0

        val (len, posX, posY, rotation) = if (isTap) {
            val defaultLen = DEFAULT_LENGTH_PT
            Quad(defaultLen, startX, startY, 0.0)
        } else {
            val cx = (startX + endX) / 2.0
            val cy = (startY + endY) / 2.0
            val deg = Math.toDegrees(atan2(dy, dx))
            val normDeg = if (deg < 0.0) deg + 360.0 else deg
            Quad(rawLen, cx, cy, normDeg)
        }

        val pins = defaultPinsFor(symbol, len)
        return CircuitElement(
            symbol = symbol,
            positionX = posX,
            positionY = posY,
            rotation = rotation,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            value = value,
            pins = pins,
            id = null,
        )
    }

    private data class Quad(val len: Double, val posX: Double, val posY: Double, val rot: Double)
}
