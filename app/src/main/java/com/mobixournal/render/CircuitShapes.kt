package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
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

    private inline fun withBasis(
        sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double,
        block: (len: Double, p: (u: Double, v: Double) -> StrokePoint) -> List<StrokePoint>,
    ): List<StrokePoint> {
        val dx = ex - sx
        val dy = ey - sy
        val len = hypot(dx, dy)
        if (len == 0.0) return line(sx, sy, ex, ey, widthPt)
        val ux = dx / len
        val uy = dy / len
        val vx = -uy
        val vy = ux
        val p = { u: Double, v: Double ->
            StrokePoint(sx + u * ux + v * vx, sy + u * uy + v * vy, widthPt)
        }
        return block(len, p)
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
        val dx = ex - sx
        val dy = ey - sy
        val len = hypot(dx, dy)
        if (len == 0.0) return listOf(line(sx, sy, ex, ey, widthPt))
        val ux = dx / len
        val uy = dy / len
        val vx = -uy
        val vy = ux
        val p = { u: Double, v: Double ->
            StrokePoint(sx + u * ux + v * vx, sy + u * uy + v * vy, widthPt)
        }
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
}
