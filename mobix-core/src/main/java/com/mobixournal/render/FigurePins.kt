package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.render.CircuitSnapping.PinTarget
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Pins on **every figure**, as Xnotes draws them — which figure may join which, and where its pins sit.
 *
 * Three families carry pins: a **circuit** (its symbols' terminals), a **wire** (a line's two ends) and
 * an **arrow** (its tail and tip). What a gesture shows is exactly what it may join, and the rule is one
 * small matrix rather than three separate ones per gesture:
 *
 * | figure in hand | marks, and joins |
 * |---|---|
 * | circuit | wires, and other parts' pins |
 * | wire (line) | parts' pins, and other wires' ends |
 * | arrow | every figure — parts, wires and arrows |
 *
 * The matrix is the whole point of the file: a mark that a gesture could not actually join would be a
 * promise the lift breaks, and a target it joins without marking was the invisible-joint bug the rings
 * were added to fix. Both shells read their chrome from [targets], so the two cannot disagree.
 *
 * Pure geometry — no Android types, no view — so it is unit-tested on the JVM (`FigurePinsTest`).
 */
enum class PinFamily {
    CIRCUIT,
    WIRE,
    ARROW,
}

object FigurePins {

    /** Two vertices closer than this are the same vertex — a head repeats its tip exactly. */
    private const val SAME_POINT_PT = 1e-6

    /** The family the shape [kind] carries when it is drawn or placed, or null for a figure with no pins. */
    fun familyOf(kind: ShapeKind): PinFamily? = when {
        CircuitElementFactory.isCircuitKind(kind) -> PinFamily.CIRCUIT
        kind == ShapeKind.LINE -> PinFamily.WIRE
        kind == ShapeKind.ARROW || kind == ShapeKind.DOUBLE_ARROW -> PinFamily.ARROW
        else -> null
    }

    /**
     * The family a committed [element] carries, or null when it has no pins — a rectangle, a chemistry
     * bond, free ink.
     *
     * A headed figure is recognised through [FigureRedraw], which reads the very vertex layouts the tools
     * draw (`FigureGeometry`), so an arrow is an arrow whether it was just drawn or reopened from a file.
     */
    fun familyOf(element: Element): PinFamily? = when (element) {
        is CircuitElement -> PinFamily.CIRCUIT
        is Stroke -> when {
            CircuitSnapping.isWire(element) -> PinFamily.WIRE
            FigureRedraw.read(element.points) != null -> PinFamily.ARROW
            else -> null
        }
        else -> null
    }

    /**
     * Whether a gesture of [actor] may join [target] — the mark it shows and the target its magnet takes.
     * An arrow is the one figure that reaches every family, and the only one that reaches an arrow's ends.
     */
    fun aimsAt(actor: PinFamily, target: PinFamily): Boolean = when (actor) {
        PinFamily.CIRCUIT, PinFamily.WIRE -> target != PinFamily.ARROW
        PinFamily.ARROW -> true
    }

    /**
     * The two points a stroke offers as pins — a wire's ends, an arrow's tail and tip.
     *
     * A headed figure traces its tip *through* the head (`tail, tip, barb, tip, barb`), so the pair cannot
     * be "first and last": the farthest two vertices are the ones the shaft runs between.
     */
    fun ends(stroke: Stroke): List<Pair<Double, Double>> {
        val pts = stroke.points
        if (pts.size < 2) return emptyList()
        var a = 0
        var b = pts.lastIndex
        var best = -1.0
        for (i in pts.indices) {
            for (j in i + 1 until pts.size) {
                val d = hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y)
                if (d > best) {
                    best = d
                    a = i
                    b = j
                }
            }
        }
        return listOf(pts[a].x to pts[a].y, pts[b].x to pts[b].y)
    }

    /**
     * The vertex indices of the two pins a figure **being drawn** offers, per `FigureGeometry`'s layouts:
     * a line is its own two ends, an arrow is `tail, tip, …`, a double arrow `…, tail, …, tip, …`.
     */
    fun endIndices(kind: ShapeKind, points: List<StrokePoint>): Pair<Int, Int> {
        if (points.size < 2) return 0 to 0
        val last = points.lastIndex
        val (a, b) = when (kind) {
            ShapeKind.ARROW -> 0 to 1
            ShapeKind.DOUBLE_ARROW -> 1 to 4
            else -> 0 to last
        }
        return a.coerceIn(0, last) to b.coerceIn(0, last)
    }

    /**
     * Pulls the two pins of a **drawn figure** onto [targets] in reach, moving every vertex that sits on
     * the same pin: a head traces its tip twice, and moving one copy would tear the figure open.
     *
     * [magnet] is the live drag's hold, one per end; null judges each end once (the lift, a test).
     */
    fun snapEnds(
        points: List<StrokePoint>,
        kind: ShapeKind,
        targets: List<PinTarget>,
        thresholdPt: Double,
        magnet: EndsMagnet? = null,
    ): List<StrokePoint> {
        if (points.size < 2 || targets.isEmpty()) return points
        val (startIndex, endIndex) = endIndices(kind, points)
        val start = points[startIndex]
        val end = points[endIndex]
        val snapStart = magnet?.snapStart(start.x, start.y, targets, thresholdPt)
            ?: CircuitSnapping.findSnapTarget(start.x, start.y, targets, thresholdPt)?.let { it.absX to it.absY }
        val snapEnd = magnet?.snapEnd(end.x, end.y, targets, thresholdPt)
            ?: CircuitSnapping.findSnapTarget(end.x, end.y, targets, thresholdPt)?.let { it.absX to it.absY }
        if (snapStart == null && snapEnd == null) return points
        return points.map { p ->
            when {
                snapStart != null && same(p, start) -> p.copy(x = snapStart.first, y = snapStart.second)
                snapEnd != null && same(p, end) -> p.copy(x = snapEnd.first, y = snapEnd.second)
                else -> p
            }
        }
    }

    private fun same(a: StrokePoint, b: StrokePoint): Boolean =
        abs(a.x - b.x) <= SAME_POINT_PT && abs(a.y - b.y) <= SAME_POINT_PT

    /**
     * Every pin on [page] a gesture of [actor] may join ([aimsAt]): the parts' pins and the two ends of
     * every wire and arrow. [exclude] leaves out the elements being moved, and [near] skips an element
     * whose pins cannot be within the caller's reach.
     */
    fun targets(
        page: Page,
        actor: PinFamily,
        exclude: Set<ElementRef> = emptySet(),
        near: Bounds? = null,
    ): List<PinTarget> {
        val out = mutableListOf<PinTarget>()
        page.layers.forEachIndexed { layerIndex, layer ->
            layer.elements.forEachIndexed { elementIndex, element ->
                if (ElementRef(layerIndex, elementIndex) in exclude) return@forEachIndexed
                val family = familyOf(element) ?: return@forEachIndexed
                if (!aimsAt(actor, family)) return@forEachIndexed
                when (element) {
                    is CircuitElement -> {
                        if (element.pins.isEmpty()) return@forEachIndexed
                        if (near != null && !envelopeOf(element).intersects(near)) return@forEachIndexed
                        for (pin in element.pins) {
                            val (x, y) = CircuitSnapping.pinAbsolutePosition(element, pin)
                            out += PinTarget(element.id, pin.id, x, y, pin.connectedTo)
                        }
                    }
                    is Stroke -> {
                        if (near != null && !envelopeOf(element).intersects(near)) return@forEachIndexed
                        for ((x, y) in ends(element)) out += PinTarget(null, CircuitSnapping.WIRE_END_PIN_ID, x, y, null)
                    }
                    else -> Unit
                }
            }
        }
        return out
    }

    /**
     * The family a **moved selection** aims with: [PinFamily.ARROW] as soon as one arrow is in it (an
     * arrow joins every figure), otherwise the parts-and-wires set. Null when nothing moved has pins.
     */
    fun moveActor(page: Page, refs: Set<ElementRef>): PinFamily? {
        val families = refs.mapNotNull { ref ->
            page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)?.let(::familyOf)
        }
        if (families.isEmpty()) return null
        return if (PinFamily.ARROW in families) PinFamily.ARROW else PinFamily.CIRCUIT
    }

    /** The box an element's pins occupy — a superset of everything it can offer as a target. */
    private fun envelopeOf(element: Element): Bounds = when (element) {
        is CircuitElement -> {
            val pins = element.pins.map { CircuitSnapping.pinAbsolutePosition(element, it) }
            if (pins.isEmpty()) {
                Bounds(element.positionX, element.positionY, element.positionX, element.positionY)
            } else {
                Bounds(
                    pins.minOf { it.first }, pins.minOf { it.second },
                    pins.maxOf { it.first }, pins.maxOf { it.second },
                )
            }
        }
        is Stroke -> Bounds(
            element.points.minOf { it.x }, element.points.minOf { it.y },
            element.points.maxOf { it.x }, element.points.maxOf { it.y },
        )
        else -> Bounds(0.0, 0.0, 0.0, 0.0)
    }
}
