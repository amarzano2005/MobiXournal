package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
     * shaded row by row like a hand-scribbled triangle. Rows pitch at the stroke width so they merge
     * into one tone, and the walk enters on the base's top corner and leaves at the apex — every
     * joining segment runs along one of the triangle's own edges, so no wire crosses its interior.
     */
    private fun anodeTriangle(
        p: (u: Double, v: Double) -> StrokePoint,
        baseU: Double, apexU: Double, h: Double, widthPt: Double,
    ): List<StrokePoint> {
        val pitch = maxOf(widthPt, 0.5)
        var rows = ceil(2.0 * h / pitch).toInt().coerceAtLeast(1)
        if (rows % 2 == 0) rows++          // odd count ⇒ the last row ends on the hypotenuse
        val dy = 2.0 * h / rows
        val pts = ArrayList<StrokePoint>(rows * 2 + 2)
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
     * Ground (GND): Lead stem -> 3 parallel plates of decreasing size.
     * Retraced back to center stem so it forms a single continuous stroke.
     */
    fun ground(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val stem = len * 0.55
            val step = minOf(6.0, len * 0.12)
            val scale = minOf(1.0, len / 40.0)
            val w1 = 18.0 * scale
            val w2 = 11.0 * scale
            val w3 = 4.0 * scale

            listOf(
                p(0.0, 0.0),
                p(stem, 0.0),
                p(stem, -w1), p(stem, w1), p(stem, 0.0),
                p(stem + step, 0.0),
                p(stem + step, -w2), p(stem + step, w2), p(stem + step, 0.0),
                p(stem + 2 * step, 0.0),
                p(stem + 2 * step, -w3), p(stem + 2 * step, w3), p(stem + 2 * step, 0.0),
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

            // Top edge -> Gate back top -> Input 2
            pts += p(gBack, -h)
            pts += p(gBack, -inSep)
            pts += p(0.0, -inSep)
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

            // Top edge -> Gate back top -> Input 2
            pts += p(gBack, -h)
            pts += p(gBack, -inSep)
            pts += p(0.0, -inSep)
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

            // Retrace along Input 2 to outer back arc
            pts += p(outerBackU(-inSep), -inSep)

            // Outer back arc up to top corner (gBack - xorGap, -h)
            for (i in 1..backSteps) {
                val v = -inSep + (-h - (-inSep)) * (i.toDouble() / backSteps)
                pts += p(outerBackU(v), v)
            }

            // Outer back arc all the way down to bottom corner (gBack - xorGap, h)
            for (i in 1..(backSteps * 2)) {
                val v = -h + (2.0 * h) * (i.toDouble() / (backSteps * 2))
                pts += p(outerBackU(v), v)
            }

            // Retrace back to Input 1 intersection so the outer arc is complete
            for (i in 1..backSteps) {
                val v = h + (inSep - h) * (i.toDouble() / backSteps)
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

            // Retrace along Input 2 to outer back arc
            pts += p(outerBackU(-inSep), -inSep)

            // Outer back arc up to top corner (gBack - xorGap, -h)
            for (i in 1..backSteps) {
                val v = -inSep + (-h - (-inSep)) * (i.toDouble() / backSteps)
                pts += p(outerBackU(v), v)
            }

            // Outer back arc all the way down to bottom corner (gBack - xorGap, h)
            for (i in 1..(backSteps * 2)) {
                val v = -h + (2.0 * h) * (i.toDouble() / (backSteps * 2))
                pts += p(outerBackU(v), v)
            }

            // Retrace back to Input 1 intersection so the outer arc is complete
            for (i in 1..backSteps) {
                val v = h + (inSep - h) * (i.toDouble() / backSteps)
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
        return listOf(stroke1, stroke2)
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
}
