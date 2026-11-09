package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.hypot
import kotlin.math.PI

/** Append/revise a numeric tail, then assemble ONE closed contour with the existing winding and caps. */
class IncrementalTaper {
    private var xs = FloatArray(0); private var ys = FloatArray(0); private var half = FloatArray(0)
    private var tx = FloatArray(0); private var ty = FloatArray(0)
    val retainedPoints: Int get() = xs.size
    private var count = 0
    private var projection: List<Float> = emptyList()
    var processedPoints: Long = 0
        private set

    fun outline(points: List<StrokePoint>, scale: Float, offsetX: Float, offsetY: Float,
        stablePrefix: Int): FloatArray {
        val n = points.size
        var from = stablePrefix.coerceIn(0, minOf(count, n))
        val nextProjection = listOf(scale, offsetX, offsetY)
        if (projection != nextProjection || n < count) from = 0
        projection = nextProjection
        if (n > xs.size) {
            val capacity = maxOf(n, maxOf(32, xs.size * 2))
            xs = xs.copyOf(capacity); ys = ys.copyOf(capacity); half = half.copyOf(capacity)
            tx = tx.copyOf(capacity); ty = ty.copyOf(capacity)
        }
        for (i in from until n) {
            xs[i] = offsetX + (points[i].x * scale).toFloat()
            ys[i] = offsetY + (points[i].y * scale).toFloat()
            half[i] = ((points[i].width * scale) / 2.0).toFloat().coerceAtLeast(0.25f)
        }
        for (i in maxOf(0, from - 1) until n) {
            val a = maxOf(0, i - 1); val b = minOf(n - 1, i + 1)
            var dx = xs[b] - xs[a]; var dy = ys[b] - ys[a]
            if (dx == 0f && dy == 0f && i > 0) { dx = xs[i] - xs[i - 1]; dy = ys[i] - ys[i - 1] }
            val length = hypot(dx, dy)
            when {
                length > 0f -> { tx[i] = dx / length; ty[i] = dy / length }
                i > 0 -> { tx[i] = tx[i - 1]; ty[i] = ty[i - 1] }
                else -> { tx[i] = 1f; ty[i] = 0f }
            }
            processedPoints++
        }
        count = n
        if (n < 2) return FloatArray(0)
        val out = FloatArray(4 * n + 32)
        var k = 0
        for (i in 0 until n) { out[k++] = xs[i] - ty[i] * half[i]; out[k++] = ys[i] + tx[i] * half[i] }
        val piHalf = PI.toFloat() / 2f
        k = InkGeometry.addCap(out, k, xs[n - 1], ys[n - 1], tx[n - 1], ty[n - 1], half[n - 1], piHalf, -piHalf)
        for (i in n - 1 downTo 0) { out[k++] = xs[i] + ty[i] * half[i]; out[k++] = ys[i] - tx[i] * half[i] }
        InkGeometry.addCap(out, k, xs[0], ys[0], tx[0], ty[0], half[0], -piHalf, -3f * piHalf)
        return out
    }
}
