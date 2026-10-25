package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure geometric functions for calculating circuit pin coordinates and magnetic snapping.
 *
 * All coordinates round-trip through the native `.mobix` format and synthesis export.
 */
object CircuitSnapping {

    /** Distance threshold in points within which two pins magnetically snap. */
    const val SNAP_THRESHOLD_PT: Double = 14.0

    /**
     * The magnet's reach in **screen** pixels. A fit-to-width page draws a point at well under a
     * pixel, so a 14pt reach can be three pixels wide on screen: unhittable by eye, which is why a
     * wire used to join a pin only by luck.
     */
    const val SNAP_REACH_PX: Double = 22.0

    /**
     * The most a pin may reach in page points. Without a ceiling, a heavily zoomed-out page would turn
     * the screen-sized reach into a pull across a visible fraction of the sheet.
     */
    const val MAX_SNAP_PT: Double = 36.0

    /**
     * The magnet's reach in page points for a view [scale] (page points → screen pixels):
     * [SNAP_THRESHOLD_PT] on a zoomed-in page, and never less than [SNAP_REACH_PX] on screen when
     * zoomed out, so the target is always big enough to aim at — but no more than [MAX_SNAP_PT]. A
     * scale that is not a real number (a page not laid out yet) falls back to the document-space
     * reach.
     */
    fun thresholdFor(scale: Float, reachPx: Double = SNAP_REACH_PX): Double {
        // A page with no layout yet has no scale to convert with: keep the document-space reach
        // rather than the widest one, so nothing snaps further than it looks.
        if (!scale.isFinite() || scale <= 0f) return SNAP_THRESHOLD_PT
        return (reachPx / scale.toDouble()).coerceIn(SNAP_THRESHOLD_PT, MAX_SNAP_PT)
    }

    /** How far a wire's intermediate vertices may sit off its own straight segment, in points. */
    private const val WIRE_STRAIGHTNESS_PT: Double = 1.0

    /** Represents a magnetic snapping candidate pin on the page. */
    data class PinTarget(
        val circuitId: String?,
        val pinId: String,
        val absX: Double,
        val absY: Double,
        val net: String?,
    )

    /**
     * Resolves the absolute page-coordinate position of [pin] on [circuit],
     * taking into account component anchor position and rotation.
     */
    fun pinAbsolutePosition(circuit: CircuitElement, pin: CircuitPin): Pair<Double, Double> {
        if (circuit.rotation == 0.0) {
            return (circuit.positionX + pin.relX) to (circuit.positionY + pin.relY)
        }
        val rad = Math.toRadians(circuit.rotation)
        val c = cos(rad)
        val s = sin(rad)
        val px = circuit.positionX + pin.relX * c - pin.relY * s
        val py = circuit.positionY + pin.relX * s + pin.relY * c
        return px to py
    }

    /**
     * Converts an absolute page point ([absX], [absY]) to relative unrotated coordinates for [circuit].
     */
    fun pinRelativePosition(circuit: CircuitElement, absX: Double, absY: Double): Pair<Double, Double> {
        val dx = absX - circuit.positionX
        val dy = absY - circuit.positionY
        if (circuit.rotation == 0.0) return dx to dy
        val rad = -Math.toRadians(circuit.rotation)
        val c = cos(rad)
        val s = sin(rad)
        val rx = dx * c - dy * s
        val ry = dx * s + dy * c
        return rx to ry
    }

    /**
     * Collects all pin targets across [circuits], excluding pins on [excludeCircuitId].
     */
    fun collectTargets(circuits: List<CircuitElement>, excludeCircuitId: String? = null): List<PinTarget> {
        val out = mutableListOf<PinTarget>()
        for (c in circuits) {
            if (c.id != null && c.id == excludeCircuitId) continue
            for (p in c.pins) {
                val (x, y) = pinAbsolutePosition(c, p)
                out += PinTarget(c.id, p.id, x, y, p.connectedTo)
            }
        }
        return out
    }

    /**
     * Collects every component pin on [page], excluding pins belonging to [excludeCircuitId].
     *
     * Pins are the page's **only** magnetic targets. Ink is never a target, however straight it is:
     * a pen stroke lying near a wire is the user's own drawing, not a terminal, and pulling a line
     * onto it (or onto another line's end) drew connections the user never asked for. The magnet
     * exists to join a connector to a **component**, and only a connector (the line tool) or a
     * placed component ever asks for it.
     */
    fun collectAllTargets(page: Page, excludeCircuitId: String? = null): List<PinTarget> =
        collectTargets(
            page.layers.flatMap { it.elements.filterIsInstance<CircuitElement>() },
            excludeCircuitId,
        )

    /**
     * Everything a **component being placed** may join: the page's pins, plus both ends of every wire
     * already drawn ([isWire]).
     *
     * A wire is a connector, so a pin meeting a wire's end is the very joint the magnet exists to make
     * — the same joint as a wire drawn onto a pin, seen from the other side. Without the wire's ends in
     * the target list the connection only closed when the component happened to be placed **first**:
     * draw the wire, then drop the resistor on its end, and the two never met. Only a component asks
     * for these targets, and only a wire's *ends* are offered, so a symbol is still never pulled onto
     * any ink lying beside it.
     */
    fun collectPlacementTargets(page: Page): List<PinTarget> {
        val pins = collectAllTargets(page)
        val wires = buildList {
            for (layer in page.layers) {
                for (element in layer.elements) {
                    val stroke = element as? Stroke ?: continue
                    if (!isWire(stroke)) continue
                    val first = stroke.points.first()
                    val last = stroke.points.last()
                    add(PinTarget(null, "wire-end", first.x, first.y, null))
                    add(PinTarget(null, "wire-end", last.x, last.y, null))
                }
            }
        }
        return pins + wires
    }

    /**
     * What the shape [kind] being drawn may magnetise to, or null when nothing may pull it.
     *
     * A **wire** aims at pins ([collectAllTargets]); a **component being placed** aims at pins *and* at
     * the ends of wires already on the page ([collectPlacementTargets]) — a wire drawn before the part
     * it feeds used to leave the two unjoined, because a pin could only ever be the target and never the
     * seeker's own end. Everything else — a rectangle, a bond, the chemist's ring — gets null, so free
     * ink is never pulled off the user's hand.
     */
    fun gestureTargets(kind: ShapeKind, page: Page): List<PinTarget>? = when {
        CircuitElementFactory.isCircuitKind(kind) -> collectPlacementTargets(page)
        CircuitElementFactory.isConnectorKind(kind) -> collectAllTargets(page)
        else -> null
    }

    /**
     * Where the pins of the component being dragged from ([startX], [startY]) to ([endX], [endY])
     * would land — the rings the live preview draws, and the points its magnet aims with.
     *
     * It is [CircuitElementFactory]'s own placement rule (a tap is the default length, a drag spans
     * the two ends), so what is shown while placing is what the commit mints.
     */
    fun previewTargets(
        kind: ShapeKind,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
    ): List<PinTarget> = CircuitElementFactory
        .placedElementFor(kind.name, startX, startY, endX, endY)
        ?.let { collectTargets(listOf(it)) }
        .orEmpty()

    /**
     * Whether [stroke] is a **wire** — a single straight segment (the line tool's own two points, or a
     * straight line carrying redundant collinear vertices from a `.xopp`). A closed figure (triangle,
     * arrow, chemistry ring) has vertices that do not lie on one line, so it is ink, never a connector:
     * only a wire's own ends are dragged toward a pin, and a sketch's corners never are.
     */
    fun isWire(stroke: Stroke): Boolean {
        if (stroke.points.size !in 2..4) return false
        val first = stroke.points.first()
        val last = stroke.points.last()
        val dx = last.x - first.x
        val dy = last.y - first.y
        val len = hypot(dx, dy)
        // A closed stroke ends where it started: a figure, not a wire.
        if (len < 1e-6) return false
        return stroke.points.all { p ->
            abs((p.x - first.x) * dy - (p.y - first.y) * dx) / len <= WIRE_STRAIGHTNESS_PT
        }
    }

    /**
     * Snaps a page point ([x], [y]) for a shape-tool gesture.
     *
     * [magnetic] is the connector switch: only a wire (line tool) or a placed component asks for it.
     * Every other figure keeps its exact dragged geometry, and only the paper ruling can move it when
     * [snapToGrid] is on. Without that gate the magnet fired on every rectangle, ellipse and chemistry
     * bond drawn near an existing stroke.
     *
     * [scale] is the view's page-points-to-pixels factor and sets how far the magnet reaches
     * ([thresholdFor]); it defaults to 100%, the reach of a page with no zoom to read.
     *
     * [targets] replaces the page's pins for a gesture whose reach is wider — a component being
     * placed aims at [collectPlacementTargets] (pins and wire ends) instead. A caught target is
     * returned as-is, so the magnet still beats the ruling under *Snap to grid*.
     */
    fun snapPoint(
        page: Page,
        x: Double,
        y: Double,
        snapToGrid: Boolean,
        magnetic: Boolean = false,
        scale: Float = 1f,
        targets: List<PinTarget>? = null,
    ): Pair<Double, Double> {
        if (magnetic) {
            val snap = findSnapTarget(x, y, targets ?: collectAllTargets(page), thresholdFor(scale))
            if (snap != null) return snap.absX to snap.absY
        }
        return if (snapToGrid) Snapping.lattice(page.background, page.width).snap(x, y) else x to y
    }

    /**
     * Snaps the first and last points of a drawn stroke to nearby pin targets if within [thresholdPt].
     */
    fun snapStrokeEndpoints(
        pts: List<StrokePoint>,
        targets: List<PinTarget>,
        thresholdPt: Double = SNAP_THRESHOLD_PT,
    ): List<StrokePoint> {
        if (pts.size < 2 || targets.isEmpty()) return pts
        val first = pts.first()
        val last = pts.last()
        val snapFirst = findSnapTarget(first.x, first.y, targets, thresholdPt)
        val snapLast = findSnapTarget(last.x, last.y, targets, thresholdPt)
        if (snapFirst == null && snapLast == null) return pts

        val res = pts.toMutableList()
        if (snapFirst != null) {
            res[0] = first.copy(x = snapFirst.absX, y = snapFirst.absY)
        }
        if (snapLast != null) {
            res[res.lastIndex] = last.copy(x = snapLast.absX, y = snapLast.absY)
        }
        return res
    }

    /**
     * Snaps translation ([rawDx], [rawDy]) of selected circuits or wires on [page] so that their
     * pins/endpoints land on a component pin.
     *
     * Only connectors are considered, and only toward component pins ([collectAllTargets] is empty
     * on a page without circuits): a wire is never a target, so dragging an ordinary figure, or a
     * wire, across the sheet is a free move unless a real pin is in reach.
     */
    fun snapTranslation(
        page: Page?,
        refs: Set<ElementRef>,
        rawDx: Double,
        rawDy: Double,
        thresholdPt: Double = SNAP_THRESHOLD_PT,
    ): Pair<Double, Double> {
        if (page == null || refs.isEmpty()) return rawDx to rawDy
        val selectedCircuits = refs.mapNotNull { ref ->
            page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? CircuitElement
        }
        val selectedStrokes = refs.mapNotNull { ref ->
            (page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? Stroke)
                ?.takeIf { isWire(it) }
        }
        if (selectedCircuits.isEmpty() && selectedStrokes.isEmpty()) return rawDx to rawDy

        // The moved parts' **own** pins are not targets: a pin cannot join itself, and offering it made the
        // magnet pull the part back onto where it already was for the first few points of every drag. By
        // position, not by id — a part read from a `.xopp` has no id, and one exclusion covered only the
        // first of a multi-part selection.
        val targets = collectTargets(
            page.layers.flatMapIndexed { layerIndex, layer ->
                layer.elements.mapIndexedNotNull { elementIndex, element ->
                    if (ElementRef(layerIndex, elementIndex) in refs) null else element as? CircuitElement
                }
            },
        )
        if (targets.isEmpty()) return rawDx to rawDy

        var bestDx = rawDx
        var bestDy = rawDy
        var bestDist = thresholdPt

        for (circuit in selectedCircuits) {
            for (pin in circuit.pins) {
                val (origX, origY) = pinAbsolutePosition(circuit, pin)
                val candX = origX + rawDx
                val candY = origY + rawDy
                for (target in targets) {
                    val dist = hypot(candX - target.absX, candY - target.absY)
                    if (dist <= bestDist) {
                        bestDist = dist
                        bestDx = target.absX - origX
                        bestDy = target.absY - origY
                    }
                }
            }
        }

        for (stroke in selectedStrokes) {
            for (pt in listOf(stroke.points.first(), stroke.points.last())) {
                val candX = pt.x + rawDx
                val candY = pt.y + rawDy
                for (target in targets) {
                    val dist = hypot(candX - target.absX, candY - target.absY)
                    if (dist <= bestDist) {
                        bestDist = dist
                        bestDx = target.absX - pt.x
                        bestDy = target.absY - pt.y
                    }
                }
            }
        }

        return bestDx to bestDy
    }

    /**
     * Finds the closest pin target within [thresholdPt] of ([absX], [absY]), or null if none is in reach.
     */
    fun findSnapTarget(
        absX: Double,
        absY: Double,
        targets: List<PinTarget>,
        thresholdPt: Double = SNAP_THRESHOLD_PT,
    ): PinTarget? {
        var closest: PinTarget? = null
        var closestDist = thresholdPt
        for (target in targets) {
            val dist = hypot(absX - target.absX, absY - target.absY)
            if (dist <= closestDist) {
                closest = target
                closestDist = dist
            }
        }
        return closest
    }

    /**
     * Result of snapping a pin drag.
     */
    data class SnapResult(
        val snappedPin: CircuitPin,
        val targetPin: PinTarget?,
        val targetNet: String?,
    )

    /**
     * Evaluates magnetic snapping for [pin] of [circuit] when positioned at page coordinates ([absX], [absY]).
     *
     * If within [thresholdPt] of an existing pin in [targets], snaps onto it and assigns the net name.
     * If pulled free beyond [thresholdPt], snaps to the relative offset of ([absX], [absY]) with connectedTo = null.
     */
    fun snapPinDrag(
        circuit: CircuitElement,
        pin: CircuitPin,
        absX: Double,
        absY: Double,
        targets: List<PinTarget>,
        thresholdPt: Double = SNAP_THRESHOLD_PT,
    ): SnapResult {
        val target = findSnapTarget(absX, absY, targets, thresholdPt)
        return if (target != null) {
            val (relX, relY) = pinRelativePosition(circuit, target.absX, target.absY)
            val netName = target.net ?: "net_${target.circuitId ?: "c"}_${target.pinId}"
            val updated = pin.copy(relX = relX, relY = relY, connectedTo = netName)
            SnapResult(updated, target, netName)
        } else {
            val (relX, relY) = pinRelativePosition(circuit, absX, absY)
            val updated = pin.copy(relX = relX, relY = relY, connectedTo = null)
            SnapResult(updated, null, null)
        }
    }
}
