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

    /**
     * Diode: Lead-in wire -> triangular anode body -> vertical cathode bar -> lead-out wire.
     */
    fun diode(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(16.0, len * 0.28)
            val aStart = len * 0.32
            val cEnd = len * 0.68
            listOf(
                p(0.0, 0.0),
                p(aStart, 0.0),
                p(aStart, -h),
                p(cEnd, 0.0),
                p(aStart, h),
                p(aStart, 0.0),
                p(cEnd, 0.0),
                p(cEnd, -h),
                p(cEnd, h),
                p(cEnd, 0.0),
                p(len, 0.0),
            )
        }

    /**
     * LED: Diode body with two light emission arrows radiating outward.
     */
    fun led(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(16.0, len * 0.28)
            val aStart = len * 0.32
            val cEnd = len * 0.68
            val rayLen = minOf(7.0, len * 0.12)
            val barb = rayLen * 0.45
            val r1u = aStart + (cEnd - aStart) * 0.35
            val r1v = -h * 1.15
            val r2u = aStart + (cEnd - aStart) * 0.75
            val r2v = -h * 1.15

            val pts = ArrayList<StrokePoint>()
            pts += p(0.0, 0.0)
            pts += p(aStart, 0.0)
            pts += p(aStart, -h)
            pts += p(cEnd, 0.0)
            pts += p(aStart, h)
            pts += p(aStart, 0.0)
            pts += p(cEnd, 0.0)
            pts += p(cEnd, -h)

            // Ray 1
            pts += p(r1u, r1v)
            val tip1u = r1u + rayLen
            val tip1v = r1v - rayLen
            pts += p(tip1u, tip1v)
            pts += p(tip1u - barb, tip1v)
            pts += p(tip1u, tip1v)
            pts += p(tip1u, tip1v + barb)
            pts += p(tip1u, tip1v)
            pts += p(r1u, r1v)

            // Ray 2
            pts += p(r2u, r2v)
            val tip2u = r2u + rayLen
            val tip2v = r2v - rayLen
            pts += p(tip2u, tip2v)
            pts += p(tip2u - barb, tip2v)
            pts += p(tip2u, tip2v)
            pts += p(tip2u, tip2v + barb)
            pts += p(tip2u, tip2v)
            pts += p(r2u, r2v)

            // Return to cathode bar
            pts += p(cEnd, -h)
            pts += p(cEnd, h)
            pts += p(cEnd, 0.0)
            pts += p(len, 0.0)
            pts
        }

    /**
     * Zener Diode: Diode body with cathode bar bent into characteristic 'Z' wings.
     */
    fun zenerDiode(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(16.0, len * 0.28)
            val aStart = len * 0.32
            val cEnd = len * 0.68
            val zBend = minOf(5.0, len * 0.08)
            listOf(
                p(0.0, 0.0),
                p(aStart, 0.0),
                p(aStart, -h),
                p(cEnd, 0.0),
                p(aStart, h),
                p(aStart, 0.0),
                p(cEnd, 0.0),
                // Cathode bar with Z-bend: top bends right (+u), bottom bends left (-u)
                p(cEnd, -h),
                p(cEnd + zBend, -h),
                p(cEnd, -h),
                p(cEnd, h),
                p(cEnd - zBend, h),
                p(cEnd, h),
                p(cEnd, 0.0),
                p(len, 0.0),
            )
        }

    /**
     * Operational Amplifier (Op-Amp): Inverting (-) & non-inverting (+) inputs, triangular body with
     * '-' and '+' signs, and single output pin.
     */
    fun opAmp(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(22.0, len * 0.32)
            val inSep = h * 0.50
            val gBack = len * 0.25
            val gTip = len * 0.75
            val signInset = minOf(4.0, len * 0.05)
            val signW = minOf(5.0, len * 0.07)
            val pts = ArrayList<StrokePoint>()

            // Inverting (-) input pin
            pts += p(0.0, -inSep)
            pts += p(gBack, -inSep)

            // Minus sign inside triangle
            pts += p(gBack + signInset, -inSep)
            pts += p(gBack + signInset + signW, -inSep)
            pts += p(gBack, -inSep)

            // Triangle top corner
            pts += p(gBack, -h)
            pts += p(gTip, 0.0)

            // Output lead (out to len and back to tip)
            pts += p(len, 0.0)
            pts += p(gTip, 0.0)

            // Triangle bottom corner
            pts += p(gBack, h)

            // Retrace back edge to close triangle and reach non-inverting (+) input
            pts += p(gBack, -h)
            pts += p(gBack, inSep)

            // Plus sign inside triangle
            val plusC = gBack + signInset + signW / 2.0
            val plusHalf = signW / 2.0
            pts += p(gBack + signInset, inSep)
            pts += p(gBack + signInset + signW, inSep)
            pts += p(plusC, inSep)
            pts += p(plusC, inSep - plusHalf)
            pts += p(plusC, inSep + plusHalf)
            pts += p(plusC, inSep)
            pts += p(gBack, inSep)

            // Non-inverting (+) input pin
            pts += p(0.0, inSep)
            pts
        }

    /**
     * BJT NPN Transistor: Base lead at origin -> base bar -> collector lead (top) ->
     * emitter lead (bottom) with arrow pointing outwards (away from base).
     */
    fun bjtNpn(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(18.0, len * 0.30)
            val bPos = len * 0.35
            val cJuncV = -h * 0.45
            val eJuncV = h * 0.45
            val cornerU = len * 0.75
            val barb = minOf(4.5, len * 0.06)
            val pts = ArrayList<StrokePoint>()

            // Base wire
            pts += p(0.0, 0.0)
            pts += p(bPos, 0.0)

            // Base bar: down to bottom corner, then up to collector junction
            pts += p(bPos, h)
            pts += p(bPos, cJuncV)

            // Collector branch
            pts += p(cornerU, -h)
            pts += p(len, -h)
            pts += p(cornerU, -h)
            pts += p(bPos, cJuncV)

            // Base bar up to top, then down to emitter junction
            pts += p(bPos, -h)
            pts += p(bPos, eJuncV)

            // Emitter branch with arrow pointing outward
            val midU = (bPos + cornerU) / 2.0
            val midV = (eJuncV + h) / 2.0
            pts += p(midU, midV)
            val arrowBarbU = barb * 0.8
            val arrowBarbV = barb * 0.6
            pts += p(midU - arrowBarbU, midV - arrowBarbV)
            pts += p(midU, midV)
            pts += p(midU - arrowBarbU + arrowBarbV * 0.5, midV)
            pts += p(midU, midV)
            pts += p(cornerU, h)
            pts += p(len, h)
            pts
        }

    /**
     * BJT PNP Transistor: Base lead at origin -> base bar -> collector lead (top) ->
     * emitter lead (bottom) with arrow pointing inwards (towards base).
     */
    fun bjtPnp(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<StrokePoint> =
        withBasis(sx, sy, ex, ey, widthPt) { len, p ->
            val h = minOf(18.0, len * 0.30)
            val bPos = len * 0.35
            val cJuncV = -h * 0.45
            val eJuncV = h * 0.45
            val cornerU = len * 0.75
            val barb = minOf(4.5, len * 0.06)
            val pts = ArrayList<StrokePoint>()

            // Base wire
            pts += p(0.0, 0.0)
            pts += p(bPos, 0.0)

            // Base bar: down to bottom corner, then up to collector junction
            pts += p(bPos, h)
            pts += p(bPos, cJuncV)

            // Collector branch
            pts += p(cornerU, -h)
            pts += p(len, -h)
            pts += p(cornerU, -h)
            pts += p(bPos, cJuncV)

            // Base bar up to top, then down to emitter junction
            pts += p(bPos, -h)
            pts += p(bPos, eJuncV)

            // Emitter branch with arrow pointing inward (towards base)
            val midU = (bPos + cornerU) / 2.0
            val midV = (eJuncV + h) / 2.0
            pts += p(midU, midV)
            val arrowBarbU = barb * 0.8
            val arrowBarbV = barb * 0.6
            pts += p(midU + arrowBarbU, midV + arrowBarbV)
            pts += p(midU, midV)
            pts += p(midU + arrowBarbU - arrowBarbV * 0.5, midV)
            pts += p(midU, midV)
            pts += p(cornerU, h)
            pts += p(len, h)
            pts
        }

    /**
     * DC Voltage Source (Battery): Positive long plate and negative short plate separated by an air gap.
     * Decomposes into two disconnected strokes to preserve the open gap in .xopp format.
     */
    fun dcSource(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> {
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
