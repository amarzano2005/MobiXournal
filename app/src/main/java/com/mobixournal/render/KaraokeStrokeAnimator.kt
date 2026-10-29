package com.mobixournal.render

import android.graphics.Canvas
import android.graphics.Paint
import com.mobixournal.format.model.StrokePoint

/**
 * The **Android half** of the karaoke replay: the glowing nib, and the surface's live state.
 *
 * The rules — which strokes are dimmed, which are being written, and where a point sits on the clip's
 * timeline — are not here any more: they are `KaraokeStrokeAnimator` in `mobix-core`, so the window can
 * answer the same questions about the same stroke (`docs/audio-inking-sync.md`). What stayed is the one
 * thing that needs `android.graphics`: the tip's circle, drawn over the page.
 */
fun drawKaraokeTipIndicator(
    canvas: Canvas,
    tip: StrokePoint,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    colorArgb: Int,
    paint: Paint = defaultKaraokeTipPaint,
) {
    val cx = (tip.x * scale + offsetX).toFloat()
    val cy = (tip.y * scale + offsetY).toFloat()
    val radius = (tip.width * scale * 1.5).toFloat().coerceAtLeast(4f)
    paint.color = colorArgb
    paint.alpha = 180
    canvas.drawCircle(cx, cy, radius, paint)
}

private val defaultKaraokeTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
}

private var surfaceKaraokeState: KaraokeStrokeAnimator.State? = null

/** The current live audio karaoke playback state on this surface. */
var DrawingSurfaceView.karaokeState: KaraokeStrokeAnimator.State?
    get() = surfaceKaraokeState
    set(value) {
        surfaceKaraokeState = value
        render()
    }
