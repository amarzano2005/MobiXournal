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
 * Four families carry pins: a **circuit** (its symbols' terminals), a **wire** (a line's two ends), an
 * **arrow** (its tail, tip and barbs) and a plain **figure** (a rectangle's corners and the middle of each
 * side, a triangle's, an ellipse's outline). The last one is a *target only*: a rectangle is not a
 * connector, so it never magnetises anything itself — it is what a connector attaches to.
 *
 * What a gesture shows is exactly what it may join, and the rule is one small matrix per family:
 *
 * | figure in hand | marks, and joins |
 * |---|---|
 * | circuit | parts' pins, and wires |
 * | wire (line) | parts' pins, wires, and figures' pins |
 * | arrow | every figure — parts, wires, arrows and drawn figures |
 *
 * The matrix is the whole point of the file: a mark a gesture could not join would be a promise the lift
 * breaks, and a target joined without a mark was the invisible-joint bug the rings were added to fix. Both
 * shells read their chrome from [targets], so the two cannot disagree.
 *
 * Pure geometry — no Android types, no view — so it is unit-tested on the JVM (`FigurePinsTest`).
 */
enum class PinFamily {
    CIRCUIT,
    WIRE,
    ARROW,

    /** A drawn figure — a rectangle, an ellipse, a triangle: pins, but never a connector. */
    FIGURE,
}

object FigurePins {

    /** Two vertices closer than this are the same vertex — a head repeats its tip exactly. */
    private const val SAME_POINT_PT = 1e-6

    /** A closed outline up to this many vertices is a polygon: corners **and** side midpoints get a pin. */
    private const val MAX_POLYGON_VERTICES = 12

    /** Points on a dense outline (an ellipse's polyline) that get a pin. */
    private const val OUTLINE_PINS = 8

    private val ARROW_KINDS = setOf(
        ShapeKind.ARROW, ShapeKind.DOUBLE_ARROW, ShapeKind.COORDINATE_AXIS, ShapeKind.DIMENSION,
    )

    private val FIGURE_KINDS = setOf(
        ShapeKind.RECTANGLE, ShapeKind.ELLIPSE, ShapeKind.TRIANGLE, ShapeKind.SQUARE,
        ShapeKind.TRAPEZOID, ShapeKind.POLYGON, ShapeKind.CHEM_BENZENE,
    )

    /** The family the shape [kind] carries when it is drawn or placed, or null for one with no pins. */
    fun familyOf(kind: ShapeKind): PinFamily? = when {
        CircuitElementFactory.isCircuitKind(kind) -> PinFamily.CIRCUIT
        kind == ShapeKind.LINE -> PinFamily.WIRE
        kind in ARROW_KINDS -> PinFamily.ARROW
        kind in FIGURE_KINDS -> PinFamily.FIGURE
        else -> null
    }

    /**
     * Whether [page] already holds ink an element of [kind] could join, in **either** direction.
     *
     * This is the question a mark **in flight** asks. While a figure is being drawn its pins are shown, so the
     * border a connector will attach to is visible as it is laid down; on a page with nothing to join those
     * dots are furniture on empty paper. A connector asks the matrix it aims with ([targets]); a drawn figure
     * is a target rather than a seeker, so what it asks for is a connector that can reach it.
     */
    fun hasCompany(
        page: Page,
        kind: ShapeKind,
        /** Elements whose pins are the **actor** — a moved selection — so they are not company for it. */
        exclude: Set<ElementRef> = emptySet(),
    ): Boolean {
        val family = familyOf(kind) ?: return false
        return if (family == PinFamily.FIGURE) {
            targets(page, PinFamily.WIRE, exclude = exclude).isNotEmpty() ||
                targets(page, PinFamily.ARROW, exclude = exclude).isNotEmpty()
        } else {
            targets(page, family, exclude = exclude).isNotEmpty()
        }
    }

    /**
     * The family [kind] **aims with**, or null — only a connector magnetises. A rectangle has pins, but it
     * is a target rather than a seeker, so its own drag keeps the hand's geometry.
     */
    fun aimFamily(kind: ShapeKind): PinFamily? = familyOf(kind)?.takeIf { it != PinFamily.FIGURE }

    /** The family a committed [element] carries, or null when it has no pins. */
    fun familyOf(element: Element): PinFamily? = when (element) {
        is CircuitElement -> PinFamily.CIRCUIT
        is Stroke -> familyOfPoints(element.points)
        else -> null
    }

    /**
     * The family a **stroke's vertices** show, read the way the tools wrote them: a straight segment is a
     * wire, a headed figure is an arrow (`FigureRedraw` reads the very layouts `FigureGeometry` emits), and
     * a closed outline is a figure.
     */
    fun familyOfPoints(points: List<StrokePoint>): PinFamily? {
        if (points.size < 2) return null
        if (points.size in 2..4 && straight(points)) return PinFamily.WIRE
        if (FigureRedraw.read(points) != null) return PinFamily.ARROW
        if (closed(points)) return PinFamily.FIGURE
        return null
    }

    /**
     * Whether a gesture of [actor] may join [target] — the mark it shows and the target its magnet takes.
     *
     * A circuit reaches wires; a wire reaches parts, wires and drawn figures; an arrow reaches every
     * family. Only an arrow may join another arrow's ends.
     */
    fun aimsAt(actor: PinFamily, target: PinFamily): Boolean = when (actor) {
        PinFamily.CIRCUIT -> target == PinFamily.CIRCUIT || target == PinFamily.WIRE
        PinFamily.WIRE -> target != PinFamily.ARROW
        PinFamily.ARROW -> true
        PinFamily.FIGURE -> false
    }

    /**
     * The two points a stroke offers as its **ends** — a wire's ends, an arrow's tail and tip.
     *
     * A headed figure traces its tip *through* the head (`tail, tip, barb, tip, barb`), so the pair cannot
     * be "first and last": the farthest two vertices are the ones the shaft runs between.
     */
    fun ends(points: List<StrokePoint>): List<Pair<Double, Double>> {
        if (points.size < 2) return emptyList()
        var a = 0
        var b = points.lastIndex
        var best = -1.0
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val d = hypot(points[i].x - points[j].x, points[i].y - points[j].y)
                if (d > best) {
                    best = d
                    a = i
                    b = j
                }
            }
        }
        return listOf(points[a].x to points[a].y, points[b].x to points[b].y)
    }

    /**
     * Every pin a figure's vertices carry: a wire's two ends, an arrow's tail, tip and barbs, a closed
     * figure's corners **and the middle of each side** (an ellipse's dense outline is sampled instead).
     *
     * [kind] only settles a tie the vertices cannot: a figure drawn at tap size is one point, and a
     * rectangle's own drag is still a rectangle.
     */
    fun pinsOf(kind: ShapeKind, points: List<StrokePoint>): List<Pair<Double, Double>> =
        when (familyOfPoints(points) ?: familyOf(kind)) {
            PinFamily.WIRE -> ends(points)
            PinFamily.ARROW -> listOfNotNull(tip(points))
            PinFamily.FIGURE -> borderPins(points)
            else -> emptyList()
        }

    /**
     * The **one** point an arrow attaches to: its tip.
     *
     * An arrow is a pointer, not a wire: what it joins is where it points, so only the head end carries a
     * pin — the tail is the hand's own point and the barbs are the drawing of the head, not anchors.
     */
    fun tip(points: List<StrokePoint>): Pair<Double, Double>? = ends(points).lastOrNull()

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
            ShapeKind.COORDINATE_AXIS -> 1 to 4
            else -> 0 to last
        }
        return a.coerceIn(0, last) to b.coerceIn(0, last)
    }

    /**
     * Pulls the two ends of a **drawn connector** onto [targets] in reach, moving every vertex that sits on
     * the same end: a head traces its tip twice, and moving one copy would tear the figure open.
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
        // An arrow attaches by its **tip** only, so its tail is never pulled: what the user drew is kept.
        val arrowOnly = (familyOfPoints(points) ?: familyOf(kind)) == PinFamily.ARROW
        val snapStart = if (arrowOnly) {
            null
        } else {
            magnet?.snapStart(start.x, start.y, targets, thresholdPt)
                ?: CircuitSnapping.findSnapTarget(start.x, start.y, targets, thresholdPt)?.let { it.absX to it.absY }
        }
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

    /**
     * Every pin on [page] a gesture of [actor] may join ([aimsAt]). [exclude] leaves out the elements being
     * moved, and [near] skips an element whose pins cannot be within the caller's reach.
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
                        for ((x, y) in pinsOf(family, element.points)) {
                            out += PinTarget(null, CircuitSnapping.WIRE_END_PIN_ID, x, y, null)
                        }
                    }
                    else -> Unit
                }
            }
        }
        return out
    }

    /**
     * The family a **moved selection** aims with: an arrow as soon as one is in it, else a wire, else a
     * part. Null when what moves carries no connector — a moved rectangle joins nothing, it is joined.
     */
    fun moveActor(page: Page, refs: Set<ElementRef>): PinFamily? {
        val families = refs.mapNotNull { ref ->
            page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)?.let(::familyOf)
        }.toSet()
        if (families.isEmpty()) return null
        return when {
            PinFamily.ARROW in families -> PinFamily.ARROW
            PinFamily.WIRE in families -> PinFamily.WIRE
            PinFamily.CIRCUIT in families -> PinFamily.CIRCUIT
            else -> null
        }
    }

    /** Pins of a family whose points we have, not the element (`pinsOf` without a [ShapeKind]). */
    private fun pinsOf(family: PinFamily, points: List<StrokePoint>): List<Pair<Double, Double>> =
        when (family) {
            PinFamily.WIRE -> ends(points)
            PinFamily.ARROW -> listOfNotNull(tip(points))
            PinFamily.FIGURE -> borderPins(points)
            else -> emptyList()
        }

    /** The distinct vertices of a stroke, in order — an arrow's tail, tip and two barbs. */
    private fun uniqueVertices(points: List<StrokePoint>): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        for (p in points) {
            val at = p.x to p.y
            if (out.none { hypot(it.first - at.first, it.second - at.second) <= SAME_POINT_PT }) out += at
        }
        return out
    }

    /**
     * A closed figure's pins: one at every corner and one at the middle of every side, which is what a
     * connector needs to attach to a border rather than only to a corner.
     *
     * A dense outline (an ellipse, whose polyline is 24 to 96 points) gets [OUTLINE_PINS] points spread
     * around it instead of a pin per segment.
     */
    private fun borderPins(points: List<StrokePoint>): List<Pair<Double, Double>> {
        val ring = uniqueVertices(points).let { vertices ->
            if (vertices.size > 1 && hypot(
                    vertices.first().first - vertices.last().first,
                    vertices.first().second - vertices.last().second,
                ) <= SAME_POINT_PT
            ) {
                vertices.dropLast(1)
            } else {
                vertices
            }
        }
        if (ring.size < 3) return ring
        if (ring.size > MAX_POLYGON_VERTICES) {
            return (0 until OUTLINE_PINS).map { i ->
                ring[i * (ring.size - 1) / (OUTLINE_PINS - 1)]
            }
        }
        val out = ring.toMutableList()
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            out += ((a.first + b.first) / 2.0) to ((a.second + b.second) / 2.0)
        }
        return out
    }

    /** A stroke that ends where it began, and is not just a wiggle: a drawn figure. */
    private fun closed(points: List<StrokePoint>): Boolean =
        points.size >= 4 &&
            hypot(points.first().x - points.last().x, points.first().y - points.last().y) <= SAME_POINT_PT &&
            uniqueVertices(points).size >= 3

    /** Every vertex on one straight segment — what a wire is. */
    private fun straight(points: List<StrokePoint>): Boolean {
        val first = points.first()
        val last = points.last()
        val dx = last.x - first.x
        val dy = last.y - first.y
        val len = hypot(dx, dy)
        if (len < 1e-6) return false
        return points.all { p -> abs((p.x - first.x) * dy - (p.y - first.y) * dx) / len <= 1.0 }
    }

    private fun same(a: StrokePoint, b: StrokePoint): Boolean =
        abs(a.x - b.x) <= SAME_POINT_PT && abs(a.y - b.y) <= SAME_POINT_PT

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
