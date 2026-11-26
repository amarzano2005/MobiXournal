package com.mobixournal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.rememberChromeColor
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

/**
 * Concave quarter-circle fillet tucked into the inner corner of the header band and the side rail's
 * lane so the two arms read as one continuous L-frame around the recessed canvas.
 *
 * Drawn as a lightweight Compose `Canvas` overlay driven by [LocalChromeInsetController] rather than
 * clipping the native `SurfaceView`, keeping Home ↔ Editor, Focus Mode, Split View, and handedness
 * flips flicker-free.
 */
@Composable
internal fun BoxScope.ChromeCornerFillet(
    railOnLeft: Boolean,
    railProgressProvider: () -> Float,
    laneProgressProvider: () -> Float,
    railFullPx: Float,
    radiusDp: Dp = 16.dp,
) {
    val controller = LocalChromeInsetController.current
    val isDark = chromeIsDark()
    val frameFill = rememberGlassSurfaceColor().compositeOver(rememberChromeColor())
    val hairline = ModernTheme.hairlineBorder(isDark)

    Canvas(
        modifier = Modifier
            .align(if (railOnLeft) Alignment.TopStart else Alignment.TopEnd)
            .size(radiusDp)
            .graphicsLayer {
                val rp = railProgressProvider().coerceIn(0f, 1f)
                val lp = laneProgressProvider().coerceIn(0f, 1f)
                val reveal = controller.entryReveal.coerceIn(0f, 1f)
                val topVis = if (controller.topInsetPx > 1f) 1f else 0f
                translationX = if (railOnLeft) railFullPx * rp else -railFullPx * rp
                alpha = (reveal * rp * (1f - lp) * topVis).coerceIn(0f, 1f)
            },
    ) {
        val r = minOf(size.width, size.height)
        if (r <= 0f) return@Canvas
        val path = Path()
        val arc = Path()
        if (railOnLeft) {
            val oval = Rect(0f, 0f, 2f * r, 2f * r)
            path.moveTo(0f, 0f)
            path.lineTo(r, 0f)
            path.arcTo(oval, startAngleDegrees = 270f, sweepAngleDegrees = -90f, forceMoveTo = false)
            path.close()
            arc.arcTo(oval, startAngleDegrees = 270f, sweepAngleDegrees = -90f, forceMoveTo = true)
        } else {
            val oval = Rect(-r, 0f, r, 2f * r)
            path.moveTo(r, 0f)
            path.lineTo(0f, 0f)
            path.arcTo(oval, startAngleDegrees = 270f, sweepAngleDegrees = 90f, forceMoveTo = false)
            path.close()
            arc.arcTo(oval, startAngleDegrees = 270f, sweepAngleDegrees = 90f, forceMoveTo = true)
        }
        drawPath(path = path, color = frameFill)
        drawPath(path = arc, color = hairline, style = Stroke(width = 1.dp.toPx()))
    }
}
