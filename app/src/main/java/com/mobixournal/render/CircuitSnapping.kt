package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
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
