package com.mobixournal.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.mobixournal.format.model.Page
import kotlin.math.roundToInt

/**
 * The page, **rasterised once** and blitted while a stroke is being drawn.
 *
 * The measurement that asked for this (`--probe-ink`, `docs/desktop-tablet.md`) is the same one that
 * produced `desktop-pen-lag-behind-the-nib`: on a blank A4 a packet became ink in ~13 ms, but on a dense
 * page the 95th percentile was ~29 ms and the worst frame ~48 ms. The difference is not the pen's path —
 * it is that every frame re-painted the **whole sheet**: paper, picture, every living entity compiled
 * through `LivingInk`, every text box measured, and all four thousand strokes of `udiff.xopp`.
 *
 * Per packet that is wasted work: nothing on the page changes while the hand draws. So the static page is
 * painted into an image once per (page, viewport, size) and each frame becomes one textured quad plus the
 * stroke in progress — a cost that no longer depends on how much ink the document holds.
 *
 * The cache is **not** a second renderer. [paint] calls the same `drawPageContent` the direct path used,
 * so a cached page and a freshly painted one are the same pixels; only *when* that work happens changes.
 * Recomputed on: an edit (a new [Page] instance), a scroll or a zoom (the viewport), a window resize, a
 * layer switched off, or a page open at another picture (`PageBackdrop` identity).
 *
 * Plain values in, [DrawScope] out, so a JVM test drives it without a window (`PageRasterTest`).
 */
internal class PageRaster {

    private var image: ImageBitmap? = null
    private var key: Key? = null
    private val canvasScope = CanvasDrawScope()

    /** How many times the page was actually rasterised — the number a test asserts on. */
    var builds: Int = 0
        private set

    /** How many frames were served from the cache — one per repaint, in the editor. */
    var blits: Int = 0
        private set

    /** What a rasterised image is valid for; every field is something that changes the pixels. */
    private data class Key(
        val page: Page,
        val offsetX: Float,
        val offsetY: Float,
        val zoom: Float,
        val density: Float,
        val width: Int,
        val height: Int,
        val backdrop: PageBackdrop?,
        val hiddenLayers: Set<Int>,
    )

    /**
     * Paint the sheet for this frame: the cache's image, or a fresh one.
     *
     * The image is the **canvas's own size**, not the page's: a page at 400 % is a sheet of twenty
     * screens, and caching that would cost tens of megabytes to show a window's worth of it. At 1:1 there
     * is no resampling, so the blit is exact and [FilterQuality.None] keeps it off the filtering path.
     */
    fun draw(
        target: DrawScope,
        page: Page,
        viewport: Viewport,
        zoom: Float,
        density: Float,
        backdrop: PageBackdrop?,
        hiddenLayers: Set<Int>,
        measurer: TextMeasurer,
    ) {
        val width = target.size.width.roundToInt().coerceAtLeast(1)
        val height = target.size.height.roundToInt().coerceAtLeast(1)
        val wanted = Key(
            page = page,
            offsetX = viewport.offsetX,
            offsetY = viewport.offsetY,
            zoom = zoom,
            density = density,
            width = width,
            height = height,
            backdrop = backdrop,
            hiddenLayers = hiddenLayers,
        )
        var bitmap = image
        // A resize reallocates; anything else repaints into the image it already has, so scrolling and
        // zooming do not churn a multi-megabyte bitmap per notch.
        if (bitmap == null || bitmap.width != width || bitmap.height != height) {
            bitmap = ImageBitmap(width, height)
            image = bitmap
            key = null
        }
        if (wanted != key) {
            paint(bitmap, wanted, measurer)
            key = wanted
            builds++
        }
        target.drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(width, height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, height),
            filterQuality = FilterQuality.None,
        )
        blits++
    }

    /** The one place the page becomes pixels — the direct paint path, into an offscreen image. */
    private fun paint(bitmap: ImageBitmap, key: Key, measurer: TextMeasurer) {
        val canvas = Canvas(bitmap)
        canvasScope.draw(
            density = Density(key.density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(key.width.toFloat(), key.height.toFloat()),
        ) {
            // The image is reused across scrolls, so the previous frame's sheet has to go first.
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            withTransform({ translate(key.offsetX * key.density, key.offsetY * key.density) }) {
                drawPageContent(
                    page = key.page,
                    pxPerPt = key.zoom * key.density,
                    textMeasurer = measurer,
                    backdrop = key.backdrop,
                    hiddenLayers = key.hiddenLayers,
                )
            }
        }
    }
}
