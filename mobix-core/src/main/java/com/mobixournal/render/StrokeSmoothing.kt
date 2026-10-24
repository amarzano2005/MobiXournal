package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.hypot

/**
 * The jitter filter the shape recogniser runs before it looks at a stroke.
 *
 * A stylus reports a sample every few milliseconds, and a resting hand trembles: at 240 Hz the
 * tremor shows up as ±1–2 pt of high-frequency wobble around the line the user meant. The
 * recogniser's straightness test ([Inertia]) reads that wobble as curvature, so a stroke the user
 * drew as a line can sit above the line threshold and be left as drawn — the shape tool looks like
 * it "didn't notice". Filtering first is what makes the thresholds meaningful on real input.
 *
 * The filter is an **exponential moving average** (the epic's `ema`) followed by a **displacement
 * clamp**:
 *
 * 1. `p'ᵢ = α·pᵢ + (1−α)·p'ᵢ₋₁`, with the first sample kept so the stroke still starts where the
 *    tip touched down.
 * 2. The smoothed sample may move at most [MAX_SHIFT_PT] from where it really was. Without this the
 *    average *lags* a corner — a triangle's vertex would be pulled back along the incoming edge —
 *    and the corner detector would stop seeing the corner. With it, jitter (a fraction of a pt) is
 *    averaged away while a real turn (several pt of travel) stays where the user put it.
 *
 * The last sample is **not** filtered: it is the tip of the stroke, and the line fitter's
 * endpoint test ([ShapeRecognizer]) measures how far the tip strayed from the fitted line.
 *
 * Pure and Android-free, so it unit-tests on the JVM.
 */
internal object StrokeSmoothing {

    /**
     * Weight of the newest sample in the moving average. Half is the usual trade: one sample of
     * jitter is attenuated by ~2×, two by ~4×, and a genuine direction change still dominates within
     * two samples.
     */
    const val ALPHA = 0.5

    /**
     * Furthest (pt) the filter may move a sample from where it was recorded. Above the tremor
     * amplitude, below the spacing at which a hand-drawn corner starts to look like a fillet.
     */
    const val MAX_SHIFT_PT = 1.5

    /**
     * [points] with the tremor averaged out. Widths and order are preserved, so the result is still
     * the same stroke for every caller downstream (only the recogniser sees this copy).
     */
    fun smooth(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 3) return points
        val out = ArrayList<StrokePoint>(points.size)
        out += points.first()
        for (i in 1 until points.size - 1) {
            val p = points[i]
            val prev = out[i - 1]
            val targetX = ALPHA * p.x + (1 - ALPHA) * prev.x
            val targetY = ALPHA * p.y + (1 - ALPHA) * prev.y
            val dx = targetX - p.x
            val dy = targetY - p.y
            val shift = hypot(dx, dy)
            val k = if (shift > MAX_SHIFT_PT) MAX_SHIFT_PT / shift else 1.0
            out += if (k == 1.0) {
                p.copy(x = targetX, y = targetY)
            } else {
                p.copy(x = p.x + dx * k, y = p.y + dy * k)
            }
        }
        out += points.last()
        return out
    }
}
