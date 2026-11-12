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

    /**
     * Distance in points within which two pins snap when the view's scale is **unknown** — a page whose
     * layout has not run yet, or a caller with no zoom to give. Every UI path passes its scale and gets
     * [thresholdFor]'s answer instead.
     */
    const val SNAP_THRESHOLD_PT: Double = 14.0

    /**
     * The magnet's reach in **screen** pixels: the distance it keeps at every zoom, so a pin is as easy
     * to aim at on a 20 % page as on a 400 % one. A reach fixed in page points is the thing that made
     * the magnet feel greedy when zoomed in — 14 pt is 56 px at 400 % — and shy when zoomed out.
     *
     * Twelve pixels is about a fingertip's own contact patch: far enough that a pen does not have to be
     * exact, near enough that a pin beside the one aimed at is not caught instead. It used to be 20, which
     * covered a thumb's width and made the target area feel like it swallowed every joint nearby.
     */
    const val SNAP_REACH_PX: Double = 12.0

    /**
     * How much further than [SNAP_REACH_PX] a **caught** target keeps winning before the magnet lets go.
     *
     * Without it a hand resting on the edge of the reach makes the pin flicker between caught and free on
     * every jitter, and the moved part twitches between snapped and not. A target that was caught holds to
     * 1.5× the reach, which is the hysteresis a drag needs to feel settled.
     */
    const val SNAP_RELEASE_RATIO: Double = 1.5

    /**
     * The magnet's reach in page points for a view [scale] (page points → screen pixels).
     *
     * It is always [reachPx] **screen** pixels: `reachPx / scale`. There is deliberately no floor and no
     * ceiling in page points — those are exactly what turned a constant screen reach into a bigger and
     * bigger one as the page was zoomed in (`14 pt × 8` = 112 screen pixels), so a target could be caught
     * from a finger's width away. A scale that is not a real number (a page not laid out yet) falls back
     * to [SNAP_THRESHOLD_PT], the document-space reach, rather than inventing one.
     */
    fun thresholdFor(scale: Float, reachPx: Double = SNAP_REACH_PX): Double {
        // A page with no layout yet has no scale to convert with: keep the document-space reach rather
        // than a zero or an infinite one.
        if (!scale.isFinite() || scale <= 0f) return SNAP_THRESHOLD_PT
        return reachPx / scale.toDouble()
    }

    /** How far a wire's intermediate vertices may sit off its own straight segment, in points. */
    private const val WIRE_STRAIGHTNESS_PT: Double = 1.0

    /**
     * The `pinId` the end of a **wire** is offered under, by [collectMoveTargets] and
     * [collectPlacementTargets] — a connector's end is a terminal, though it belongs to no component.
     */
    const val WIRE_END_PIN_ID: String = "wire-end"

    /** Represents a magnetic snapping candidate pin on the page. */
    data class PinTarget(
        val circuitId: String?,
        val pinId: String,
        val absX: Double,
        val absY: Double,
        val net: String?,
    ) {
        /**
         * True when this target is a **figure's end** — a wire's, or an arrow's — rather than a
         * component's pin.
         *
         * Which figures may join one is [FigurePins.aimsAt]'s rule: a component and a wire may, and only
         * an arrow may join another arrow's ends.
         */
        val isWireEnd: Boolean get() = pinId == WIRE_END_PIN_ID
    }

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
    fun collectAllTargets(
        page: Page,
        excludeCircuitId: String? = null,
        /**
         * A page-space box the caller is working in. An element whose own pins lie outside it cannot
         * contribute a target that caller could catch, so it is skipped before a [PinTarget] is
         * allocated. Null keeps the whole page, and the answer is the same either way: a target
         * further away than the caller's reach could never win `findSnapTarget`.
         */
        near: Bounds? = null,
    ): List<PinTarget> =
        collectTargets(
            page.layers.flatMap { layer ->
                layer.elements.filterIsInstance<CircuitElement>()
                    .filter { it.pins.isNotEmpty() && (near == null || circuitEnvelope(it).intersects(near)) }
            },
            excludeCircuitId,
        )

    /**
     * The pins a gesture at ([x], [y]) could possibly catch within [reach] page points — the same answer
     * [collectAllTargets] gives, found without walking the pins of every symbol on the page.
     *
     * Used by the gestures that seek a target (a wire's ends, a component being placed); the pins a sheet
     * *draws* still come from the whole page, because chrome has to show every one of them.
     */
    fun collectTargetsNear(page: Page, x: Double, y: Double, reach: Double): List<PinTarget> =
        collectAllTargets(page, near = Bounds(x - reach, y - reach, x + reach, y + reach))

    /**
     * Everything a **moved selection** may join: the page's pins, plus both ends of every wire on the
     * page ([isWire]) — minus the moved elements' own geometry, which cannot join itself.
     *
     * A wire is a connector, so a pin meeting a wire's end is a joint, exactly as it is for a component
     * being *placed* (`collectPlacementTargets`). A **moved** part only ever aimed at pins, so a component
     * dragged onto the line drawn for it never joined it: the pins were marked, the drag was magnetised,
     * and the one thing the reader was aiming at was not among the targets. Only the ends of a wire are
     * offered, so a symbol is still never pulled onto ink lying beside it, and a selected wire is left out
     * of its own target list.
     *
     * These are the targets of a **component**; a moved wire keeps to the pins among them
     * (`snapTranslation`), because a line is not a terminal.
     */
    fun collectMoveTargets(
        page: Page,
        refs: Set<ElementRef>,
        /** The box the moved pins are sweeping, as in [collectAllTargets] — far elements are skipped. */
        near: Bounds? = null,
    ): List<PinTarget> {
        val actor = FigurePins.moveActor(page, refs) ?: return emptyList()
        return FigurePins.targets(page, actor, exclude = refs, near = near)
    }

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
        return FigurePins.targets(page, PinFamily.CIRCUIT)
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
    fun gestureTargets(kind: ShapeKind, page: Page): List<PinTarget>? =
        FigurePins.aimFamily(kind)?.let { FigurePins.targets(page, it) }

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
            val reach = thresholdFor(scale)
            // A target out of reach cannot win, so only the pins near the gesture are collected: the
            // page's whole pin list is not walked on every point of a drag.
            val snap = findSnapTarget(x, y, targets ?: collectTargetsNear(page, x, y, reach), reach)
            if (snap != null) return snap.absX to snap.absY
        }
        return if (snapToGrid) Snapping.lattice(page.background, page.width).snap(x, y) else x to y
    }

    /**
     * Where a snapped single point landed, and the target that caught it — what [PointMagnet] holds from
     * one frame to the next, and what a preview could light.
     */
    data class PointSnap(val x: Double, val y: Double, val target: PinTarget?)

    /**
     * [snapPoint] with the magnet's **hold**: [caught] keeps winning while the point stays within
     * [releaseRatio] × [thresholdPt] of it, and the ordinary nearest-within-reach answer takes over past
     * that. Used by the gestures whose end moves frame by frame (a wire being drawn, a part being placed),
     * so their aim does not flicker on the edge of the reach.
     */
    fun snapPointTo(
        x: Double,
        y: Double,
        targets: List<PinTarget>,
        thresholdPt: Double = SNAP_THRESHOLD_PT,
        caught: PinTarget? = null,
        releaseRatio: Double = SNAP_RELEASE_RATIO,
    ): PointSnap {
        if (caught != null) {
            val hold = thresholdPt * releaseRatio.coerceAtLeast(1.0)
            if (hypot(x - caught.absX, y - caught.absY) <= hold) return PointSnap(caught.absX, caught.absY, caught)
        }
        val target = findSnapTarget(x, y, targets, thresholdPt)
        return if (target != null) PointSnap(target.absX, target.absY, target) else PointSnap(x, y, null)
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
     * pins/endpoints land on a **terminal** — a component pin, or (for a component) the end of a wire
     * already on the page.
     *
     * Only connectors are considered, and only toward terminals ([collectMoveTargets] is empty on a
     * page with neither a circuit nor a wire): a figure, or a free stroke, keeps the hand's own
     * translation whatever it passes over.
     *
     * The two kinds of connector aim differently, which is the rule the magnet follows: a **component**
     * may join a pin or a wire's end (circuit↔circuit, circuit↔line), while a **wire** may join a pin
     * only — a line is not a terminal, and two lines are never pulled together.
     */
    fun snapTranslation(
        page: Page?,
        refs: Set<ElementRef>,
        rawDx: Double,
        rawDy: Double,
        thresholdPt: Double = SNAP_THRESHOLD_PT,
    ): Pair<Double, Double> =
        snapTranslationTo(page, refs, rawDx, rawDy, thresholdPt).let { it.dx to it.dy }

    /**
     * Where a snapped move landed, and the target that caught it — the pin a shell lights while the hand
     * is still down, and the one [PinMagnet] holds from one frame to the next.
     */
    data class TranslationSnap(val dx: Double, val dy: Double, val target: PinTarget?)

    /**
     * [snapTranslation], plus the magnet's **hold**: [caught] is the target the previous frame landed on,
     * and it keeps winning while the hand stays within [releaseRatio] × [thresholdPt] of it. Further than
     * that it is let go, and the ordinary nearest-within-reach search answers again.
     *
     * Everything else is [snapTranslation]'s rule, unchanged: only connectors magnetise, a component may
     * join a pin or a wire's end, a wire may join a pin only, and the moved elements' own pins are never
     * targets.
     */
    fun snapTranslationTo(
        page: Page?,
        refs: Set<ElementRef>,
        rawDx: Double,
        rawDy: Double,
        thresholdPt: Double = SNAP_THRESHOLD_PT,
        caught: PinTarget? = null,
        releaseRatio: Double = SNAP_RELEASE_RATIO,
    ): TranslationSnap {
        if (page == null || refs.isEmpty()) return TranslationSnap(rawDx, rawDy, null)
        val moving = movingEnds(page, refs)
        if (moving.isEmpty()) return TranslationSnap(rawDx, rawDy, null)

        // The hold is what the window has to cover: a caught target may still win from up to
        // releaseRatio × the reach away, so both the spatial filter and the search use that wider box —
        // and the ordinary search is the narrower one, inside the same list.
        val hold = thresholdPt * releaseRatio.coerceAtLeast(1.0)
        val targets = collectMoveTargets(page, refs, near = windowOf(moving, rawDx, rawDy, hold))
        if (targets.isEmpty()) return TranslationSnap(rawDx, rawDy, null)

        if (caught != null) {
            val held = nearestAlignment(moving, listOf(caught), rawDx, rawDy, hold)
            if (held != null) return TranslationSnap(held.dx, held.dy, caught)
        }
        val best = nearestAlignment(moving, targets, rawDx, rawDy, thresholdPt)
            ?: return TranslationSnap(rawDx, rawDy, null)
        return TranslationSnap(best.dx, best.dy, best.target)
    }

    /** One end of the moved selection, at the position it had when the drag began. */
    private class MovingEnd(val x: Double, val y: Double)

    /** A moved end put on a target: the translation that does it, and the target it joined. */
    private class Alignment(val dx: Double, val dy: Double, val target: PinTarget)

    /** The ends of what [refs] holds that the magnet can move — a component's pins, a wire's two ends. */
    private fun movingEnds(page: Page, refs: Set<ElementRef>): List<MovingEnd> {
        val out = ArrayList<MovingEnd>()
        for (ref in refs) {
            when (val element = page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)) {
                is CircuitElement -> for (pin in element.pins) {
                    val (x, y) = pinAbsolutePosition(element, pin)
                    out += MovingEnd(x, y)
                }
                // A wire's ends and an arrow's tail and tip are the pins a move can land on.
                is Stroke -> if (FigurePins.familyOf(element) != null) {
                    for ((x, y) in FigurePins.ends(element.points)) out += MovingEnd(x, y)
                }
                else -> Unit
            }
        }
        return out
    }

    /** The nearest end/target pair within [limit], or null — the magnet's one rule of choice. */
    private fun nearestAlignment(
        moving: List<MovingEnd>,
        targets: List<PinTarget>,
        rawDx: Double,
        rawDy: Double,
        limit: Double,
    ): Alignment? {
        var best: Alignment? = null
        var bestDist = limit
        for (end in moving) {
            val candX = end.x + rawDx
            val candY = end.y + rawDy
            for (target in targets) {
                val dist = hypot(candX - target.absX, candY - target.absY)
                if (dist <= bestDist) {
                    bestDist = dist
                    best = Alignment(target.absX - end.x, target.absY - end.y, target)
                }
            }
        }
        return best
    }

    /** The box the moved ends sweep, grown by [pad]: every target that can win lies inside it. */
    private fun windowOf(moving: List<MovingEnd>, rawDx: Double, rawDy: Double, pad: Double): Bounds {
        var left = Double.POSITIVE_INFINITY
        var top = Double.POSITIVE_INFINITY
        var right = Double.NEGATIVE_INFINITY
        var bottom = Double.NEGATIVE_INFINITY
        for (end in moving) {
            val x = end.x + rawDx
            val y = end.y + rawDy
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        return Bounds(left - pad, top - pad, right + pad, bottom + pad)
    }

    /** The box a symbol's pins occupy — a superset of everything it can offer as a target. */
    private fun circuitEnvelope(circuit: CircuitElement): Bounds {
        var left = Double.POSITIVE_INFINITY
        var top = Double.POSITIVE_INFINITY
        var right = Double.NEGATIVE_INFINITY
        var bottom = Double.NEGATIVE_INFINITY
        for (pin in circuit.pins) {
            val (x, y) = pinAbsolutePosition(circuit, pin)
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        if (left > right) return Bounds(circuit.positionX, circuit.positionY, circuit.positionX, circuit.positionY)
        return Bounds(left, top, right, bottom)
    }

    /** The box a wire's two ends occupy. */
    private fun wireEnvelope(wire: Stroke): Bounds {
        val first = wire.points.first()
        val last = wire.points.last()
        return Bounds(
            minOf(first.x, last.x), minOf(first.y, last.y),
            maxOf(first.x, last.x), maxOf(first.y, last.y),
        )
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
