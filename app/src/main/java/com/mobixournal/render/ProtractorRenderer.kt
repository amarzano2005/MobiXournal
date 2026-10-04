package com.mobixournal.render

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the virtual protractor guide on the drawing surface canvas.
 * Draws the semicircular face, diameter base line, center crosshair,
 * graduation tick marks at 5° / 15° / 30° intervals, and degree labels.
 */
internal object ProtractorRenderer {

    private val path = Path()

    fun draw(
        canvas: Canvas,
        g: DrawingGuide.Protractor,
        chrome: CanvasChrome,
        scale: Float,
        toViewX: (Double) -> Float,
        toViewY: (Double) -> Float,
    ) {
        val cx = toViewX(g.x)
        val cy = toViewY(g.y)
        val rPx = (g.radius * scale).toFloat()
        if (rPx <= 5f) return

        val ux = g.ux.toFloat()
        val uy = g.uy.toFloat()
        val nx = g.nx.toFloat()
        val ny = g.ny.toFloat()

        // 1. Semicircular face
        path.reset()
        val p180X = cx - rPx * ux
        val p180Y = cy - rPx * uy
        val p0X = cx + rPx * ux
        val p0Y = cy + rPx * uy

        path.moveTo(p180X, p180Y)
        path.lineTo(p0X, p0Y)
        val steps = 36
        for (i in 0..steps) {
            val a = (PI * i / steps).toFloat()
            val rx = cx + rPx * (cos(a) * ux + sin(a) * nx)
            val ry = cy + rPx * (cos(a) * uy + sin(a) * ny)
            path.lineTo(rx, ry)
        }
        path.close()

        canvas.drawPath(path, chrome.guideFill)
        canvas.drawPath(path, chrome.guideStroke)

        // 2. Center crosshair
        val crossR = 6f
        canvas.drawLine(cx - crossR * ux, cy - crossR * uy, cx + crossR * ux, cy + crossR * uy, chrome.guideStroke)
        canvas.drawLine(cx, cy, cx + crossR * nx, cy + crossR * ny, chrome.guideStroke)

        // 3. Graduation ticks and angle numbers
        for (deg in 0..180 step 5) {
            val a = Math.toRadians(deg.toDouble()).toFloat()
            val cosA = cos(a)
            val sinA = sin(a)

            val dirX = cosA * ux + sinA * nx
            val dirY = cosA * uy + sinA * ny

            val isMajor = deg % 30 == 0 || deg == 45 || deg == 135
            val isMedium = deg % 15 == 0 && !isMajor
            val tickLen = when {
                isMajor -> 10f
                isMedium -> 7f
                else -> 4f
            }

            val outerX = cx + rPx * dirX
            val outerY = cy + rPx * dirY
            val innerX = cx + (rPx - tickLen) * dirX
            val innerY = cy + (rPx - tickLen) * dirY

            canvas.drawLine(outerX, outerY, innerX, innerY, chrome.guideStroke)

            // Degree labels for major angles
            if (isMajor && rPx > 50f) {
                val textR = rPx - tickLen - 8f
                val tx = cx + textR * dirX
                val ty = cy + textR * dirY + 3f
                canvas.drawText("$deg°", tx, ty, chrome.guideText)
            }
        }
    }
}
