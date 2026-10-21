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
     * Collects all pin targets from circuits on [page], along with the endpoints of wire strokes
     * ([isWire]), excluding pins belonging to [excludeCircuitId].
     *
     * Wire endpoints are only targets on a page that actually holds a circuit: the magnet exists to
     * join a wire to a component, so on a page with no components there is nothing to join and plain
     * lines must not be pulled onto each other.
     */
    fun collectAllTargets(page: Page, excludeCircuitId: String? = null): List<PinTarget> {
        val out = mutableListOf<PinTarget>()
        val circuits = page.layers.flatMap { it.elements.filterIsInstance<CircuitElement>() }
        out.addAll(collectTargets(circuits, excludeCircuitId))
        if (circuits.isEmpty()) return out
        for (layer in page.layers) {
            for (element in layer.elements) {
                if (element is Stroke && isWire(element)) {
                    val first = element.points.first()
                    val last = element.points.last()
                    out += PinTarget(null, "stroke_start", first.x, first.y, null)
                    out += PinTarget(null, "stroke_end", last.x, last.y, null)
                }
            }
        }
        return out
    }

    /**
     * Whether [stroke] is a **wire** — a single straight segment (the line tool's own two points, or a
     * straight line carrying redundant collinear vertices from a `.xopp`). A closed figure (triangle,
     * arrow, chemistry ring) has vertices that do not lie on one line, so it is ink, never a terminal:
     * the magnet must not treat a sketch's corners as connection points.
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
     */
    fun snapPoint(
        page: Page,
        x: Double,
        y: Double,
        snapToGrid: Boolean,
        magnetic: Boolean = false,
        thresholdPt: Double = SNAP_THRESHOLD_PT,
    ): Pair<Double, Double> {
        if (magnetic) {
            val snap = findSnapTarget(x, y, collectAllTargets(page), thresholdPt)
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
     * Snaps translation ([rawDx], [rawDy]) of selected circuits or wires on [page] so that
     * pins/endpoints align with existing pin targets on the page.
     *
     * Only connectors are considered, and only toward real connection targets
     * ([collectAllTargets] is empty on a page without circuits), so dragging an ordinary figure
     * across the sheet is a free move.
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

        val excludeId = selectedCircuits.firstOrNull()?.id
        val targets = collectAllTargets(page, excludeCircuitId = excludeId)
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
