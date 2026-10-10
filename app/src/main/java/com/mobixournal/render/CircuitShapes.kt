package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geometry generating vertex paths for electronic circuit components and logic gates.
 * All shapes are oriented along the user's drag vector (start -> end).
 * Designed to round-trip losslessly through `.xopp` as standard constant-width strokes.
 */
object CircuitShapes {

    private fun line(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double) =
        listOf(StrokePoint(sx, sy, widthPt), StrokePoint(ex, ey, widthPt))

    /**
     * The drag's local frame: its length, plus a map from (u, v) — along the drag, and across it —
     * to page pt. `v` is 90° clockwise from `u`, so a left-to-right drag puts positive v downwards.
     * Null for a zero-length drag, which every shape then falls back to a plain line for.
     */
    private fun frame(
        sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double,
    ): Pair<Double, (u: Double, v: Double) -> StrokePoint>? {
        val dx = ex - sx
        val dy = ey - sy
        val len = hypot(dx, dy)
        if (len == 0.0) return null
        val ux = dx / len
        val uy = dy / len
        return len to { u: Double, v: Double ->
            StrokePoint(sx + u * ux - v * uy, sy + u * uy + v * ux, widthPt)
        }
    }

    private inline fun withBasis(
        sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double,
        block: (len: Double, p: (u: Double, v: Double) -> StrokePoint) -> List<StrokePoint>,
    ): List<StrokePoint> {
        val (len, p) = frame(sx, sy, ex, ey, widthPt)
            ?: return line(sx, sy, ex, ey, widthPt)
        return block(len, p)
    }

    /**
     * The diode family's anode triangle, drawn solid: a `.xopp` stroke cannot be filled, so it is
     * shaded row by row like a hand-scribbled triangle, rows pitched at the stroke width so they
     * merge into one tone.
     *
     * The rows alone would leave a **scalloped** slanted edge: each row ends in a round cap centred
     * on the hypotenuse, so the stroke reaches its half width past the edge at every row — a sawtooth
     * fringing the hypotenuse — and the stretches of edge between two facing row ends are never
     * stroked at all. Walking the triangle's true perimeter last covers both: the silhouette becomes
     * the edge itself, offset by exactly the stroke's half width, like a hand-inked triangle. The
     * perimeter runs *along* the edges, so still nothing is drawn across the interior.
     */
    private fun anodeTriangle(
        p: (u: Double, v: Double) -> StrokePoint,
        baseU: Double, apexU: Double, h: Double, widthPt: Double,
    ): List<StrokePoint> {
        val pitch = maxOf(widthPt, 0.5)
        var rows = ceil(2.0 * h / pitch).toInt().coerceAtLeast(1)
        if (rows % 2 == 0) rows++          // odd count ⇒ the last row ends on the hypotenuse
        val dy = 2.0 * h / rows
        val pts = ArrayList<StrokePoint>(rows * 2 + 5)
        pts += p(baseU, -h)
        for (i in 0 until rows) {
            val v = -h + (i + 0.5) * dy
            val rightU = baseU + (apexU - baseU) * (1.0 - abs(v) / h)
            if (i % 2 == 0) {
                pts += p(baseU, v)
                pts += p(rightU, v)
            } else {
                pts += p(rightU, v)
                pts += p(baseU, v)
            }
        }
        // The true outline, last: apex -> top corner -> bottom corner -> apex.
        pts += p(apexU, 0.0)
        pts += p(baseU, -h)
        pts += p(baseU, h)
        pts += p(apexU, 0.0)
        return pts
    }

    /**
     * Resistor: Lead-in wire -> 6 zigzag peaks -> lead-out wire.
     */
    fun resistor(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val lead = len * 0.20
            val body = len * 0.60
            val peaks = 6
            val seg = body / peaks
            val amp = minOf(14.0, len * 0.22)
            val pts = ArrayList<StrokePoint>(peaks * 2 + 4)
            pts += p(0.0, 0.0)
            pts += p(lead, 0.0)
            for (i in 0 until peaks) {
                val ySign = if (i % 2 == 0) -1.0 else 1.0
                pts += p(lead + (i + 0.5) * seg, ySign * amp)
                pts += p(lead + (i + 1.0) * seg, 0.0)
            }
            pts += p(len, 0.0)
            pts
        }

    /**
     * Inductor: Lead-in wire -> 4 semicircular loops -> lead-out wire.
     */
    fun inductor(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val lead = len * 0.15
            val loops = 4
            val coilLen = len * 0.70
            val loopW = coilLen / loops
            val radius = loopW / 2.0
            val pts = ArrayList<StrokePoint>(loops * 9 + 4)
            pts += p(0.0, 0.0)
            pts += p(lead, 0.0)
            for (i in 0 until loops) {
                val cx = lead + i * loopW + radius
                val steps = 8
                for (s in 0..steps) {
                    val theta = PI * s / steps
                    val u = cx - radius * cos(theta)
                    val v = -radius * sin(theta)
                    pts += p(u, v)
                }
            }
            pts += p(len, 0.0)
            pts
        }

    /**
     * Ground (GND): the wire the symbol terminates, with the earth symbol hanging below it.
     *
     * The drag is the **wire** — [0, len], exactly as it is for the resistor, the capacitor and the
     * diode — and the symbol hangs off its midpoint on the +v side (downwards for a left-to-right
     * drag): a stem, then three plates of decreasing width, drawn as one stroke that retraces back up
     * the stem and runs on to the wire's far end.
     *
     * Every size is a fraction of the drag, so stretching the wire grows the symbol with it and the
     * plates keep the classic 2 : 1.3 : 0.5 proportions. The plates used to be a fixed ~6pt apiece and
     * were laid out *along* the drag after the stem, which crowded all three into one blob at the far
     * end of the wire and left the tail of the drag empty.
     */
    fun ground(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val mid = len * 0.5
            val stem = minOf(18.0, len * 0.30)
            val step = stem * 0.45
            val v1 = stem
            val v2 = stem + step
            val v3 = stem + 2 * step
            val half1 = stem
            val half2 = stem * 0.62
            val half3 = stem * 0.24

            listOf(
                p(0.0, 0.0),
                p(mid, 0.0),
                // Stem down to the first plate, then each plate as an out-and-back rung, so the pen
                // walks from one plate to the next straight down the stem instead of cutting across.
                p(mid, v1),
                p(mid - half1, v1), p(mid + half1, v1), p(mid, v1),
                p(mid, v2),
                p(mid - half2, v2), p(mid + half2, v2), p(mid, v2),
                p(mid, v3),
                p(mid - half3, v3), p(mid + half3, v3),
                // Retrace up the stem (over strokes already laid) and out to the wire's end.
                p(mid, 0.0),
                p(len, 0.0),
            )
        }

    /**
     * Capacitor: Two separate parallel plates with lead wires.
     * Both plates are drawn symmetrically with lead-in and lead-out along the centerline axis.
     */
    fun capacitor(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> {
        val (len, p) = frame(sx, sy, ex, ey, widthPt)
            ?: return listOf(line(sx, sy, ex, ey, widthPt))
        val mid = len / 2.0
        val gap = minOf(10.0, len * 0.16)
        val h = minOf(20.0, len * 0.35)
        val p1X = mid - gap / 2.0
        val p2X = mid + gap / 2.0

        val stroke1 = listOf(
            p(0.0, 0.0),
            p(p1X, 0.0),
            p(p1X, -h),
            p(p1X, h),
            p(p1X, 0.0),
        )
        val stroke2 = listOf(
            p(p2X, 0.0),
            p(p2X, -h),
            p(p2X, h),
            p(p2X, 0.0),
            p(len, 0.0),
        )
        return listOf(stroke1, stroke2)
    }

    /**
     * AND Gate: 2 input pins, flat back, rounded front arc, 1 output pin.
     */
    fun andGate(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(20.0, len * 0.30)
            val inSep = h * 0.50
            val gBack = len * 0.25
            val gArc = len * 0.50
            val gTip = len * 0.75
            val pts = ArrayList<StrokePoint>()

            // Input 1 -> Gate back bottom
            pts += p(0.0, inSep)
            pts += p(gBack, inSep)
            pts += p(gBack, h)
            pts += p(gArc, h)

            // Front arc: bottom to tip (phi from PI/2 down to 0)
            val steps = 8
            for (i in 0..steps) {
                val phi = (PI / 2.0) * (1.0 - i.toDouble() / steps)
                pts += p(gArc + (gTip - gArc) * cos(phi), h * sin(phi))
            }

            // Output lead (out and back to tip)
            pts += p(len, 0.0)
            pts += p(gTip, 0.0)

            // Front arc: tip to top (phi from 0 down to -PI/2)
            for (i in 0..steps) {
                val phi = -(PI / 2.0) * (i.toDouble() / steps)
                pts += p(gArc + (gTip - gArc) * cos(phi), h * sin(phi))
            }

            // Top edge -> Gate back top -> Input 2 pin
            pts += p(gBack, -h)
            pts += p(gBack, -inSep)
            pts += p(0.0, -inSep)
            // Retrace the pin to the back edge and carry the back down its *middle*, between the two
            // inputs. Without it the flat back is left open where the pins meet it, and the gate reads
            // as two brackets instead of one body.
            pts += p(gBack, -inSep)
            pts += p(gBack, inSep)
            pts
        }

    /**
     * NAND Gate: AND gate body + inversion bubble at the output.
     */
    fun nandGate(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(20.0, len * 0.30)
            val inSep = h * 0.50
            val gBack = len * 0.22
            val gArc = len * 0.44
            val gTip = len * 0.68
            val bubbleR = minOf(3.5, len * 0.05)
            val bubbleC = gTip + bubbleR
            val pts = ArrayList<StrokePoint>()

            // Input 1 -> Gate back bottom
            pts += p(0.0, inSep)
            pts += p(gBack, inSep)
            pts += p(gBack, h)
            pts += p(gArc, h)

            // Front arc: bottom to tip (phi from PI/2 down to 0)
            val steps = 8
            for (i in 0..steps) {
                val phi = (PI / 2.0) * (1.0 - i.toDouble() / steps)
                pts += p(gArc + (gTip - gArc) * cos(phi), h * sin(phi))
            }

            // Inversion bubble: top half from tip to front
            val bSteps = 8
            for (i in 0..bSteps) {
                val a = PI - (PI * i / bSteps)
                pts += p(bubbleC + bubbleR * cos(a), -bubbleR * sin(a))
            }

            // Output lead (from bubble front out to len, and back to bubble front)
            pts += p(len, 0.0)
            pts += p(bubbleC + bubbleR, 0.0)

            // Bottom half of bubble back to tip
            for (i in 0..bSteps) {
                val a = (PI * i / bSteps)
                pts += p(bubbleC + bubbleR * cos(a), bubbleR * sin(a))
            }

            // Front arc: tip to top (phi from 0 down to -PI/2)
            for (i in 0..steps) {
                val phi = -(PI / 2.0) * (i.toDouble() / steps)
                pts += p(gArc + (gTip - gArc) * cos(phi), h * sin(phi))
            }

            // Top edge -> Gate back top -> Input 2 pin, then the middle of the flat back (see the
            // note in [andGate]) so the back edge is not left open between the inputs.
            pts += p(gBack, -h)
            pts += p(gBack, -inSep)
            pts += p(0.0, -inSep)
            pts += p(gBack, -inSep)
            pts += p(gBack, inSep)
            pts
        }

    /**
     * OR Gate: 2 input pins, concave curved back, convex curved edges, pointed front tip, 1 output pin.
     */
    fun orGate(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(20.0, len * 0.30)
            val inSep = h * 0.50
            val gBack = len * 0.22
            val gTip = len * 0.75
            val cDepth = h * 0.35
            val pts = ArrayList<StrokePoint>()

            fun backU(v: Double): Double = gBack + cDepth * (1.0 - (v / h) * (v / h))

            // Input 1 -> into curved back
            pts += p(0.0, inSep)
            pts += p(backU(inSep), inSep)

            // Curved back down to bottom corner (gBack, h)
            val backSteps = 6
            for (i in 1..backSteps) {
                val v = inSep + (h - inSep) * (i.toDouble() / backSteps)
                pts += p(backU(v), v)
            }

            // Curved bottom edge to tip (t from 0 to 1)
            val edgeSteps = 8
            for (i in 1..edgeSteps) {
                val t = i.toDouble() / edgeSteps
                val u = gBack + (gTip - gBack) * t
                val v = h * cos((PI / 2.0) * t)
                pts += p(u, v)
            }

            // Output lead (out and back)
            pts += p(len, 0.0)
            pts += p(gTip, 0.0)

            // Curved top edge back to top-left corner (gBack, -h)
            for (i in (edgeSteps - 1) downTo 0) {
                val t = i.toDouble() / edgeSteps
                val u = gBack + (gTip - gBack) * t
                val v = -h * cos((PI / 2.0) * t)
                pts += p(u, v)
            }

            // Curved back down to Input 2
            for (i in 1..backSteps) {
                val v = -h + (-inSep - (-h)) * (i.toDouble() / backSteps)
                pts += p(backU(v), v)
            }

            // Input 2 pin
            pts += p(0.0, -inSep)
            // The concave back's middle, between the two pins: the pins are a T-junction on the curve,
            // so the path retraces the pin back onto the edge and walks down the middle — otherwise
            // the back is left open exactly where its two inputs meet it.
            pts += p(backU(-inSep), -inSep)
            for (i in 1..(2 * backSteps)) {
                val v = -inSep + (2.0 * inSep) * (i.toDouble() / (2 * backSteps))
                pts += p(backU(v), v)
            }
            pts
        }

    /**
     * NOR Gate: OR gate body + inversion bubble at the output.
     */
    fun norGate(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(20.0, len * 0.30)
            val inSep = h * 0.50
            val gBack = len * 0.20
            val gTip = len * 0.68
            val cDepth = h * 0.35
            val bubbleR = minOf(3.5, len * 0.05)
            val bubbleC = gTip + bubbleR
            val pts = ArrayList<StrokePoint>()

            fun backU(v: Double): Double = gBack + cDepth * (1.0 - (v / h) * (v / h))

            // Input 1 -> into curved back
            pts += p(0.0, inSep)
            pts += p(backU(inSep), inSep)

            // Curved back down to bottom corner (gBack, h)
            val backSteps = 6
            for (i in 1..backSteps) {
                val v = inSep + (h - inSep) * (i.toDouble() / backSteps)
                pts += p(backU(v), v)
            }

            // Curved bottom edge to tip (t from 0 to 1)
            val edgeSteps = 8
            for (i in 1..edgeSteps) {
                val t = i.toDouble() / edgeSteps
                val u = gBack + (gTip - gBack) * t
                val v = h * cos((PI / 2.0) * t)
                pts += p(u, v)
            }

            // Inversion bubble: top half from tip to front
            val bSteps = 8
            for (i in 0..bSteps) {
                val a = PI - (PI * i / bSteps)
                pts += p(bubbleC + bubbleR * cos(a), -bubbleR * sin(a))
            }

            // Output lead (from bubble front out to len, and back to bubble front)
            pts += p(len, 0.0)
            pts += p(bubbleC + bubbleR, 0.0)

            // Bottom half of bubble back to tip
            for (i in 0..bSteps) {
                val a = (PI * i / bSteps)
                pts += p(bubbleC + bubbleR * cos(a), bubbleR * sin(a))
            }

            // Curved top edge back to top-left corner (gBack, -h)
            for (i in (edgeSteps - 1) downTo 0) {
                val t = i.toDouble() / edgeSteps
                val u = gBack + (gTip - gBack) * t
                val v = -h * cos((PI / 2.0) * t)
                pts += p(u, v)
            }

            // Curved back down to Input 2
            for (i in 1..backSteps) {
                val v = -h + (-inSep - (-h)) * (i.toDouble() / backSteps)
                pts += p(backU(v), v)
            }

            // Input 2 pin
            pts += p(0.0, -inSep)
            // The concave back's middle, between the two pins (see [orGate]).
            pts += p(backU(-inSep), -inSep)
            for (i in 1..(2 * backSteps)) {
                val v = -inSep + (2.0 * inSep) * (i.toDouble() / (2 * backSteps))
                pts += p(backU(v), v)
            }
            pts
        }

    /**
     * XOR Gate: OR gate body + detached second curved back arch.
     */
    fun xorGate(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(20.0, len * 0.30)
            val inSep = h * 0.50
            val xorGap = minOf(5.0, len * 0.06)
            val gBack = len * 0.24
            val gTip = len * 0.75
            val cDepth = h * 0.35
            val pts = ArrayList<StrokePoint>()

            fun innerBackU(v: Double): Double = gBack + cDepth * (1.0 - (v / h) * (v / h))
            fun outerBackU(v: Double): Double = (gBack - xorGap) + cDepth * (1.0 - (v / h) * (v / h))

            // Input 1 wire: from 0 through outer arc to inner arc
            pts += p(0.0, inSep)
            pts += p(innerBackU(inSep), inSep)

            // Inner curved back down to bottom corner (gBack, h)
            val backSteps = 6
            for (i in 1..backSteps) {
                val v = inSep + (h - inSep) * (i.toDouble() / backSteps)
                pts += p(innerBackU(v), v)
            }

            // Curved bottom edge to tip
            val edgeSteps = 8
            for (i in 1..edgeSteps) {
                val t = i.toDouble() / edgeSteps
                val u = gBack + (gTip - gBack) * t
                val v = h * cos((PI / 2.0) * t)
                pts += p(u, v)
            }

            // Output lead (out and back)
            pts += p(len, 0.0)
            pts += p(gTip, 0.0)

            // Curved top edge back to top-left corner (gBack, -h)
            for (i in (edgeSteps - 1) downTo 0) {
                val t = i.toDouble() / edgeSteps
                val u = gBack + (gTip - gBack) * t
                val v = -h * cos((PI / 2.0) * t)
                pts += p(u, v)
            }

            // Inner curved back down to Input 2
            for (i in 1..backSteps) {
                val v = -h + (-inSep - (-h)) * (i.toDouble() / backSteps)
                pts += p(innerBackU(v), v)
            }

            // Input 2 pin out to 0
            pts += p(0.0, -inSep)

            // The inner back's middle, between the two pins: the pins are a T-junction on the curve,
            // so the path retraces the pin onto the edge and walks down the middle before carrying on
            // to the outer arch. Without it the concave back is left open where its inputs meet it.
            pts += p(innerBackU(-inSep), -inSep)
            for (i in 1..(backSteps * 2)) {
                val v = -inSep + (2.0 * inSep) * (i.toDouble() / (backSteps * 2))
                pts += p(innerBackU(v), v)
            }

            // Out to the detached outer arch and around it in one pass.
            pts += p(outerBackU(inSep), inSep)
            for (i in 1..(backSteps * 2)) {
                val v = inSep + (-h - inSep) * (i.toDouble() / (backSteps * 2))
                pts += p(outerBackU(v), v)
            }
            for (i in 1..(backSteps * 2)) {
                val v = -h + (2.0 * h) * (i.toDouble() / (backSteps * 2))
                pts += p(outerBackU(v), v)
            }
            for (i in 1..(backSteps * 2)) {
                val v = h + (inSep - h) * (i.toDouble() / (backSteps * 2))
                pts += p(outerBackU(v), v)
            }

            pts
        }

    /**
     * XNOR Gate: XOR gate body (with detached second curved back arch) + inversion bubble at output.
     */
    fun xnorGate(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(20.0, len * 0.30)
            val inSep = h * 0.50
            val xorGap = minOf(5.0, len * 0.06)
            val gBack = len * 0.24
            val gTip = len * 0.68
            val cDepth = h * 0.35
            val bubbleR = minOf(3.5, len * 0.05)
            val bubbleC = gTip + bubbleR
            val pts = ArrayList<StrokePoint>()

            fun innerBackU(v: Double): Double = gBack + cDepth * (1.0 - (v / h) * (v / h))
            fun outerBackU(v: Double): Double = (gBack - xorGap) + cDepth * (1.0 - (v / h) * (v / h))

            // Input 1 wire: from 0 through outer arc to inner arc
            pts += p(0.0, inSep)
            pts += p(innerBackU(inSep), inSep)

            // Inner curved back down to bottom corner (gBack, h)
            val backSteps = 6
            for (i in 1..backSteps) {
                val v = inSep + (h - inSep) * (i.toDouble() / backSteps)
                pts += p(innerBackU(v), v)
            }

            // Curved bottom edge to tip
            val edgeSteps = 8
            for (i in 1..edgeSteps) {
                val t = i.toDouble() / edgeSteps
                val u = gBack + (gTip - gBack) * t
                val v = h * cos((PI / 2.0) * t)
                pts += p(u, v)
            }

            // Inversion bubble: top half from tip to front
            val bSteps = 8
            for (i in 0..bSteps) {
                val a = PI - (PI * i / bSteps)
                pts += p(bubbleC + bubbleR * cos(a), -bubbleR * sin(a))
            }

            // Output lead (from bubble front out to len, and back to bubble front)
            pts += p(len, 0.0)
            pts += p(bubbleC + bubbleR, 0.0)

            // Bottom half of bubble back to tip
            for (i in 0..bSteps) {
                val a = (PI * i / bSteps)
                pts += p(bubbleC + bubbleR * cos(a), bubbleR * sin(a))
            }

            // Curved top edge back to top-left corner (gBack, -h)
            for (i in (edgeSteps - 1) downTo 0) {
                val t = i.toDouble() / edgeSteps
                val u = gBack + (gTip - gBack) * t
                val v = -h * cos((PI / 2.0) * t)
                pts += p(u, v)
            }

            // Inner curved back down to Input 2
            for (i in 1..backSteps) {
                val v = -h + (-inSep - (-h)) * (i.toDouble() / backSteps)
                pts += p(innerBackU(v), v)
            }

            // Input 2 pin out to 0
            pts += p(0.0, -inSep)

            // The inner back's middle, between the two pins: the pins are a T-junction on the curve,
            // so the path retraces the pin onto the edge and walks down the middle before carrying on
            // to the outer arch. Without it the concave back is left open where its inputs meet it.
            pts += p(innerBackU(-inSep), -inSep)
            for (i in 1..(backSteps * 2)) {
                val v = -inSep + (2.0 * inSep) * (i.toDouble() / (backSteps * 2))
                pts += p(innerBackU(v), v)
            }

            // Out to the detached outer arch and around it in one pass.
            pts += p(outerBackU(inSep), inSep)
            for (i in 1..(backSteps * 2)) {
                val v = inSep + (-h - inSep) * (i.toDouble() / (backSteps * 2))
                pts += p(outerBackU(v), v)
            }
            for (i in 1..(backSteps * 2)) {
                val v = -h + (2.0 * h) * (i.toDouble() / (backSteps * 2))
                pts += p(outerBackU(v), v)
            }
            for (i in 1..(backSteps * 2)) {
                val v = h + (inSep - h) * (i.toDouble() / (backSteps * 2))
                pts += p(outerBackU(v), v)
            }

            pts
        }

    /**
     * NOT Gate: Input pin -> triangular body -> inversion bubble -> output pin.
     */
    fun notGate(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(16.0, len * 0.25)
            val gBack = len * 0.25
            val gTip = len * 0.65
            val bubbleR = minOf(3.5, len * 0.05)
            val bubbleC = gTip + bubbleR
            val pts = ArrayList<StrokePoint>()

            // Input lead
            pts += p(0.0, 0.0)
            pts += p(gBack, 0.0)

            // Triangle: top corner -> bottom corner -> tip -> top corner (retraced) -> tip
            pts += p(gBack, -h)
            pts += p(gBack, h)
            pts += p(gTip, 0.0)
            pts += p(gBack, -h)
            pts += p(gTip, 0.0)

            // Bubble circle
            val steps = 8
            for (i in 0..steps) {
                val a = PI - 2.0 * PI * i / steps
                pts += p(bubbleC + bubbleR * cos(a), bubbleR * sin(a))
            }

            // Output lead
            pts += p(len, 0.0)
            pts
        }

    /**
     * Diode: lead-in wire -> solid anode triangle -> vertical cathode bar -> lead-out wire. Traced as
     * one stroke: the wire stops at the triangle's base and the apex touches the cathode bar, so
     * nothing is drawn across the body's interior.
     */
    fun diode(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(16.0, len * 0.28)
            val aStart = len * 0.32
            val cEnd = len * 0.68
            val pts = ArrayList<StrokePoint>()
            pts += p(0.0, 0.0)
            pts += p(aStart, 0.0)
            pts += anodeTriangle(p, aStart, cEnd, h, widthPt)
            pts += p(cEnd, -h)
            pts += p(cEnd, h)
            pts += p(cEnd, 0.0)
            pts += p(len, 0.0)
            pts
        }

    /**
     * LED: the diode body plus two light-emission arrows. The arrows are **separate strokes** — a
     * `.xopp` stroke is one polyline, so tracing them into the body's would trail a connecting wire
     * across the gap between them. Each springs from the triangle's hypotenuse, pointing up and away.
     */
    fun led(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> {
        val (len, p) = frame(sx, sy, ex, ey, widthPt)
            ?: return listOf(line(sx, sy, ex, ey, widthPt))
        val h = minOf(16.0, len * 0.28)
        val aStart = len * 0.32
        val cEnd = len * 0.68

        val body = ArrayList<StrokePoint>()
        body += p(0.0, 0.0)
        body += p(aStart, 0.0)
        body += anodeTriangle(p, aStart, cEnd, h, widthPt)
        body += p(cEnd, -h)
        body += p(cEnd, h)
        body += p(cEnd, 0.0)
        body += p(len, 0.0)

        val rayLen = minOf(12.0, len * 0.09)
        val barb = rayLen * 0.45

        // A ray leaves the hypotenuse at [along] (a fraction of the edge), heading up and forwards.
        fun ray(along: Double): List<StrokePoint> {
            val tailU = aStart + (cEnd - aStart) * along
            val tailV = -h * (cEnd - tailU) / (cEnd - aStart)
            val tipU = tailU + rayLen
            val tipV = tailV - rayLen
            return listOf(
                p(tailU, tailV),
                p(tipU, tipV),
                p(tipU - barb, tipV),
                p(tipU, tipV),
                p(tipU, tipV + barb),
            )
        }
        return listOf(body, ray(0.30), ray(0.72))
    }

    /**
     * Zener Diode: the diode body with the cathode bar bent into its characteristic 'Z' wings.
     */
    fun zenerDiode(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(16.0, len * 0.28)
            val aStart = len * 0.32
            val cEnd = len * 0.68
            val zBend = minOf(5.0, len * 0.08)
            val pts = ArrayList<StrokePoint>()
            pts += p(0.0, 0.0)
            pts += p(aStart, 0.0)
            pts += anodeTriangle(p, aStart, cEnd, h, widthPt)
            // Cathode bar with Z-bend: top bends right (+u), bottom bends left (-u)
            pts += p(cEnd, -h)
            pts += p(cEnd + zBend, -h)
            pts += p(cEnd, -h)
            pts += p(cEnd, h)
            pts += p(cEnd - zBend, h)
            pts += p(cEnd, h)
            pts += p(cEnd, 0.0)
            pts += p(len, 0.0)
            pts
        }

    /**
     * Operational Amplifier (Op-Amp): the triangular body with its inverting (-) and non-inverting
     * (+) input pins and its output pin, plus the two input signs. The signs are **separate strokes**:
     * folded into the body's polyline they could only be reached by running each pin on inside the
     * triangle, which reads as the pins overshooting their edge.
     */
    fun opAmp(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> {
        val (len, p) = frame(sx, sy, ex, ey, widthPt)
            ?: return listOf(line(sx, sy, ex, ey, widthPt))
        val h = minOf(22.0, len * 0.32)
        val inSep = h * 0.50
        val gBack = len * 0.25
        val gTip = len * 0.75
        val bodyW = gTip - gBack
        val inset = bodyW * 0.18
        val signW = bodyW * 0.06
        val half = signW / 2.0
        val signC = gBack + inset + half

        // Input pin, up the back edge, along the top edge, out the output and back, then down the
        // bottom edge, down the back edge again and out of the second input pin.
        val body = listOf(
            p(0.0, -inSep), p(gBack, -inSep), p(gBack, -h),
            p(gTip, 0.0), p(len, 0.0), p(gTip, 0.0),
            p(gBack, h), p(gBack, -h), p(gBack, inSep), p(0.0, inSep),
        )
        val minus = listOf(
            p(signC - half, -inSep),
            p(signC + half, -inSep),
        )
        val plus = listOf(
            p(signC - half, inSep), p(signC + half, inSep), p(signC, inSep),
            p(signC, inSep - half), p(signC, inSep + half),
        )
        return listOf(body, minus, plus)
    }

    /**
     * The body both bipolar symbols share: the base lead, the circular envelope, the base bar, and
     * the collector and emitter branches. [outward] picks which way the emitter's arrow points — away
     * from the base for an NPN, back at it for a PNP.
     */
    private fun bjtBody(
        len: Double,
        p: (u: Double, v: Double) -> StrokePoint,
        outward: Boolean,
    ): List<StrokePoint> {
        val h = minOf(18.0, len * 0.30)
        val barHalf = h * 0.75
        val bPos = len * 0.42
        // Body across the drag stays compact however far it is stretched, so the envelope keeps
        // enclosing the base bar instead of sliding off the end of it.
        val span = minOf(len * 0.28, barHalf * 1.5)
        val cornerU = bPos + span
        val cJuncV = -h * 0.45
        val eJuncV = h * 0.45
        val circleR = maxOf(barHalf * 1.45, span * 1.08)
        val circleCenterU = bPos + circleR * 0.55
        val circleLeftU = circleCenterU - circleR
        val barb = minOf(4.5, span * 0.35)
        val pts = ArrayList<StrokePoint>()

        // Base wire: lead-in up to the envelope's left edge
        pts += p(0.0, 0.0)
        pts += p(circleLeftU, 0.0)

        // Circular transistor envelope
        val circleSteps = 16
        for (i in 0..circleSteps) {
            val theta = PI + 2.0 * PI * (i.toDouble() / circleSteps)
            pts += p(circleCenterU + circleR * cos(theta), circleR * sin(theta))
        }

        // Lead-in continues inside the envelope to the base bar
        pts += p(bPos, 0.0)

        // Base bar: down to its foot, then up to the collector junction
        pts += p(bPos, barHalf)
        pts += p(bPos, cJuncV)

        // Collector branch out to the lead, and back to the junction
        pts += p(cornerU, -h)
        pts += p(len, -h)
        pts += p(cornerU, -h)
        pts += p(bPos, cJuncV)

        // Base bar up to its head, then down to the emitter junction
        pts += p(bPos, -barHalf)
        pts += p(bPos, eJuncV)

        // Emitter branch, with the arrow head at its midpoint: two barbs swept back off the tip
        // along the branch's own direction, so the head reads as pointing along the lead.
        val branch = hypot(span, h - eJuncV)
        val dirU = span / branch
        val dirV = (h - eJuncV) / branch
        val tipU = (bPos + cornerU) / 2.0
        val tipV = (eJuncV + h) / 2.0
        val side = if (outward) 1.0 else -1.0
        val backU = tipU - side * dirU * barb
        val backV = tipV - side * dirV * barb
        val perpU = -dirV * barb * 0.45
        val perpV = dirU * barb * 0.45
        pts += p(tipU, tipV)
        pts += p(backU + perpU, backV + perpV)
        pts += p(tipU, tipV)
        pts += p(backU - perpU, backV - perpV)
        pts += p(tipU, tipV)

        // On to the emitter lead
        pts += p(cornerU, h)
        pts += p(len, h)
        return pts
    }

    /**
     * BJT NPN Transistor: base lead at the drag's start, circular envelope and base bar, the
     * collector and emitter leads, and the emitter's arrow pointing outwards (away from the base).
     */
    fun bjtNpn(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p -> bjtBody(len, p, outward = true) }

    /**
     * BJT PNP Transistor: the same body as the NPN, with the emitter's arrow pointing inwards
     * (back towards the base).
     */
    fun bjtPnp(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p -> bjtBody(len, p, outward = false) }

    /**
     * DC Voltage Source (Battery): Positive long plate and negative short plate separated by an air gap.
     * Decomposes into two disconnected strokes to preserve the open gap in .xopp format.
     */
    fun dcSource(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> {
        val (len, p) = frame(sx, sy, ex, ey, widthPt)
            ?: return listOf(line(sx, sy, ex, ey, widthPt))
        val mid = len / 2.0
        val gap = minOf(8.0, len * 0.14)
        val h = minOf(18.0, len * 0.32)
        val p1X = mid - gap / 2.0
        val p2X = mid + gap / 2.0

        val stroke1 = listOf(
            p(0.0, 0.0),
            p(p1X, 0.0),
            p(p1X, -h),
            p(p1X, h),
            p(p1X, 0.0),
        )
        val stroke2 = listOf(
            p(p2X, 0.0),
            p(p2X, -h * 0.5),
            p(p2X, h * 0.5),
            p(p2X, 0.0),
            p(len, 0.0),
        )
        // Polarity marks above the plates: a plus sign over the long (positive) plate and a minus
        // over the short (negative) one, each its own stroke so no wire joins them. The mark's arm is
        // kept inside half the plate gap — sized off the gap rather than the plate height, the two
        // signs grew into each other and read as one long cross.
        val markR = minOf(3.0, gap * 0.30, h * 0.30)
        val markY = -h - markR * 2.0
        val plus = listOf(
            p(p1X - markR, markY), p(p1X + markR, markY),
            p(p1X, markY), p(p1X, markY - markR), p(p1X, markY), p(p1X, markY + markR),
        )
        val minus = listOf(p(p2X - markR, markY), p(p2X + markR, markY))
        return listOf(stroke1, stroke2, plus, minus)
    }

    /**
     * Current Source: Lead-in wire -> circular body with directional current arrow -> lead-out wire.
     */
    fun currentSource(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val mid = len / 2.0
            val radius = minOf(14.0, len * 0.22)
            val barb = minOf(4.0, radius * 0.45)
            val pts = ArrayList<StrokePoint>()

            // Lead-in wire to circle left edge
            pts += p(0.0, 0.0)
            pts += p(mid - radius, 0.0)

            // Circle outline (360 degrees)
            val steps = 16
            for (i in 0..steps) {
                val theta = PI + 2.0 * PI * (i.toDouble() / steps)
                pts += p(mid + radius * cos(theta), radius * sin(theta))
            }

            // Internal arrow from circle left to circle right
            val arrowStart = mid - radius * 0.65
            val arrowTip = mid + radius * 0.65
            pts += p(arrowStart, 0.0)
            pts += p(arrowTip, 0.0)
            pts += p(arrowTip - barb, -barb)
            pts += p(arrowTip, 0.0)
            pts += p(arrowTip - barb, barb)
            pts += p(arrowTip, 0.0)

            // Lead-out wire from circle right edge to end
            pts += p(mid + radius, 0.0)
            pts += p(len, 0.0)
            pts
        }

    /**
     * Junction dot: the small node where schematic wires meet. The dot sits at the drag's start; the
     * drag's length only sizes it.
     *
     * A `.xopp` stroke cannot be filled, so the disc is **scribbled solid**: rows across it pitched at
     * the pen width, so each row's round cap meets the next one and the rows merge into one tone — the
     * same trick the diode's anode triangle is shaded with. The rim is walked last so the dot's outline
     * is a circle instead of the rungs' round-capped ends.
     *
     * It used to be a bare ring, which at any ordinary pen width left a hole in the middle: a junction
     * is a solid node, and an outline around nothing reads as a selected handle, not a connection.
     */
    fun junction(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val r = minOf(5.0, maxOf(2.5, len * 0.06))
            val pitch = maxOf(widthPt, 0.5)
            // Bounded at 16 rows: past that the rows are already closer together than any pen can
            // resolve, and the point count is what the file pays for.
            val rows = ceil(2.0 * r / pitch).toInt().coerceIn(2, 16)
            val dy = 2.0 * r / rows
            val pts = ArrayList<StrokePoint>(rows * 2 + 15)
            for (i in 0 until rows) {
                val v = -r + (i + 0.5) * dy
                val half = sqrt(maxOf(0.0, r * r - v * v))
                // Alternate the sweep so consecutive rows join end-to-end inside the disc.
                if (i % 2 == 0) {
                    pts += p(-half, v)
                    pts += p(half, v)
                } else {
                    pts += p(half, v)
                    pts += p(-half, v)
                }
            }
            // The true rim last, so the disc is round rather than scalloped.
            val steps = 12
            for (i in 0..steps) {
                val a = 2.0 * PI * (i.toDouble() / steps)
                pts += p(r * cos(a), r * sin(a))
            }
            pts
        }

    /**
     * Open switch: an incoming lead up to a hinge, a blade lifted off the far contact, and the
     * outgoing lead with a short contact tick. Two strokes, so the blade never trails a wire across
     * the open gap.
     */
    fun switchOpen(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> {
        val (len, p) = frame(sx, sy, ex, ey, widthPt)
            ?: return listOf(line(sx, sy, ex, ey, widthPt))
        val lead = len * 0.22
        val contact = len - lead
        val tick = minOf(4.0, len * 0.06)
        val blade = listOf(p(0.0, 0.0), p(lead, 0.0), p(contact - lead * 0.15, -len * 0.22))
        val far = listOf(
            p(contact, 0.0), p(len, 0.0), p(contact, 0.0),
            p(contact, -tick), p(contact, 0.0), p(contact, tick), p(contact, 0.0),
        )
        return listOf(blade, far)
    }

    /**
     * Closed switch: the blade lies along the leads, with a contact tick at each terminal.
     *
     * Three strokes — the blade and **one stroke per tick** — because a single polyline through both
     * ticks would have to jump from the first to the second, and that jump is a wire drawn diagonally
     * across the whole span, which is exactly what a closed switch must not show.
     */
    fun switchClosed(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> {
        val (len, p) = frame(sx, sy, ex, ey, widthPt)
            ?: return listOf(line(sx, sy, ex, ey, widthPt))
        val lead = len * 0.22
        val contact = len - lead
        val tick = minOf(4.0, len * 0.06)
        val blade = listOf(p(0.0, 0.0), p(len, 0.0))
        val nearTick = listOf(p(lead, -tick), p(lead, tick))
        val farTick = listOf(p(contact, -tick), p(contact, tick))
        return listOf(blade, nearTick, farTick)
    }

    /**
     * Transformer: two facing coils — the primary above the core, the secondary below it — with the
     * laminated core between them. The coils' humps point **at the core**, which is what makes the
     * two windings read as magnetically coupled rather than as two loose inductors bowing apart.
     *
     * Four strokes, **one per part**: the two coils and the two core bars. The core used to be a
     * single polyline through both bars, and the segment that carried the pen from the first bar to
     * the second drew a diagonal straight down the middle of the symbol — the stray "N".
     */
    fun transformer(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> {
        val (len, p) = frame(sx, sy, ex, ey, widthPt)
            ?: return listOf(line(sx, sy, ex, ey, widthPt))
        val lead = len * 0.15
        val coilLen = len * 0.70
        val loops = 4
        val loopW = coilLen / loops
        val radius = minOf(9.0, loopW / 2.0)
        // Half the distance between the two coil axes, and half the distance between the core bars.
        // The humps reach [radius] toward the centre, so the bars sit just inside their crests.
        val halfGap = radius * 1.9
        val coreHalf = radius * 0.75

        // A coil along the drag at [offset], its humps on the [hump] side of its own axis.
        fun coil(offset: Double, hump: Double): List<StrokePoint> {
            val pts = ArrayList<StrokePoint>()
            pts += p(0.0, offset)
            pts += p(lead, offset)
            for (i in 0 until loops) {
                val cx = lead + i * loopW + loopW / 2.0
                for (s in 0..8) {
                    val theta = PI * s / 8
                    pts += p(cx - (loopW / 2.0) * cos(theta), offset + hump * radius * sin(theta))
                }
            }
            pts += p(len, offset)
            return pts
        }

        // The two core bars run *along* the coils, one on each side of the centre line.
        val coreFrom = lead
        val coreTo = lead + coilLen
        val coreTop = listOf(p(coreFrom, -coreHalf), p(coreTo, -coreHalf))
        val coreBottom = listOf(p(coreFrom, coreHalf), p(coreTo, coreHalf))
        // Primary above (humps downward), secondary below (humps upward) — both facing the core.
        return listOf(coil(-halfGap, hump = 1.0), coil(halfGap, hump = -1.0), coreTop, coreBottom)
    }

    /**
     * Buffer gate: the plain amplifier triangle — the NOT gate without its inversion bubble — with
     * its input and output pins.
     *
     * One stroke, walked so the output leaves **from the tip** and the pins attach to the back edge
     * and the tip only: nothing is ever drawn across the triangle's interior. (Reaching the output
     * from the back edge, as an earlier trace did, scored a pen line straight through the body.)
     */
    fun bufferGate(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(16.0, len * 0.26)
            val gBack = len * 0.28
            val gTip = len * 0.72
            listOf(
                // Input pin into the back edge, up the back edge, down the upper hypotenuse…
                p(0.0, 0.0), p(gBack, 0.0),
                p(gBack, -h), p(gTip, 0.0),
                // …out of the output pin and back to the tip…
                p(len, 0.0), p(gTip, 0.0),
                // …then the lower hypotenuse and back up the back edge to close the triangle.
                p(gBack, h), p(gBack, 0.0),
            )
        }
}
