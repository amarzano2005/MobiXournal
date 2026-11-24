package com.mobixournal.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.mobixournal.format.model.Page
import kotlin.math.roundToInt

/**
 * Capture a lightweight downscaled snapshot of this canvas's visible viewport so Split View can
 * slide Pane 1 out (and back in) as pure Compose GPU content at 144 Hz without moving a
 * `SurfaceView` or flashing a black hole.
 */
internal fun DrawingSurfaceView.captureStandInBitmap(maxWidthPx: Int = 900): Bitmap? {
    val w = width
    val h = height
    if (w <= 0 || h <= 0 || layout.boxes.isEmpty()) return null
    val scale = if (w > maxWidthPx) maxWidthPx.toFloat() / w else 1f
    val dstW = (w * scale).roundToInt().coerceAtLeast(1)
    val dstH = (h * scale).roundToInt().coerceAtLeast(1)
    return runCatching {
        val bmp = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        canvas.drawColor(chrome.backdropColor)
        val visible = layout.visible(scrollY, h.toFloat())
        val prevAnimating = chromeAnimating
        chromeAnimating = true
        try {
            for (box in visible) {
                BackgroundRenderer.draw(
                    canvas, box, scrollX, scrollY, pageBitmapFor(box), emptyList(),
                    w.toFloat(), h.toFloat(),
                )
                drawPageElements(canvas, box)
                drawPageOutline(canvas, box)
            }
        } finally {
            chromeAnimating = prevAnimating
        }
        bmp
    }.getOrNull()
}

/**
 * Render a single [page] sheet onto a Compose-backed [canvas] using the exact same [PageStacker]
 * and [BackgroundRenderer] geometry as [DrawingSurfaceView], so the sliding right-pane preview
 * matches the live canvas pixel-for-pixel when Split View opens on a fresh or restored sheet.
 */
internal fun drawSplitStandInPage(
    canvas: Canvas,
    page: Page,
    viewWidthPx: Int,
    viewHeightPx: Int,
    topInsetPx: Float,
    rightInsetPx: Float,
    outlineColor: Int,
) {
    if (viewWidthPx <= 0 || viewHeightPx <= 0) return
    val stacked = PageStacker.stack(
        pages = listOf(page),
        viewWidthPx = viewWidthPx,
        gapPx = DrawingSurfaceDefaults.GAP_PX,
        zoom = 1f,
        insetLeftPx = 0f,
        insetTopPx = topInsetPx,
        insetRightPx = rightInsetPx,
        insetBottomPx = 0f,
    )
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = outlineColor
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    for (box in stacked.boxes) {
        BackgroundRenderer.draw(
            canvas, box, 0f, 0f, null, emptyList(),
            viewWidthPx.toFloat(), viewHeightPx.toFloat(),
        )
        canvas.drawRect(
            RectF(box.leftPx, box.topPx, box.leftPx + box.widthPx, box.topPx + box.heightPx),
            outline,
        )
    }
}
