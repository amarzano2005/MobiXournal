package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
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
     * Collects all pin targets from circuits on [page], along with endpoints of 2-point wire strokes,
     * excluding pins belonging to [excludeCircuitId].
     */
    fun collectAllTargets(page: Page, excludeCircuitId: String? = null): List<PinTarget> {
        val out = mutableListOf<PinTarget>()
        val circuits = page.layers.flatMap { it.elements.filterIsInstance<CircuitElement>() }
        out.addAll(collectTargets(circuits, excludeCircuitId))
        for (layer in page.layers) {
            for (element in layer.elements) {
                if (element is Stroke && element.points.size >= 2) {
                    val isLine = element.points.size in 2..4
                    if (isLine) {
                        val first = element.points.first()
                        val last = element.points.last()
                        out += PinTarget(null, "stroke_start", first.x, first.y, null)
                        out += PinTarget(null, "stroke_end", last.x, last.y, null)
                    }
                }
            }
        }
        return out
    }

    /**
     * Snaps a page point ([x], [y]) to the nearest circuit pin or wire endpoint on [page].
     * If no pin target is within [thresholdPt], falls back to paper grid lattice snapping if [snapToGrid] is true.
     */
    fun snapPoint(
        page: Page,
        x: Double,
        y: Double,
        snapToGrid: Boolean,
        thresholdPt: Double = SNAP_THRESHOLD_PT,
    ): Pair<Double, Double> {
        val targets = collectAllTargets(page)
        val snap = findSnapTarget(x, y, targets, thresholdPt)
        if (snap != null) return snap.absX to snap.absY
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
                ?.takeIf { it.points.size in 2..4 }
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
