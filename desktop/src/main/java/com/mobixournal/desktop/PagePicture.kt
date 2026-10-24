package com.mobixournal.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mobixournal.format.model.Page
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import kotlin.math.roundToInt

/**
 * The page, **recorded once per viewport** as a Skia picture and replayed while a stroke is drawn.
 *
 * The cost that this removes is Kotlin's, not Skia's. `drawPageContent` recompiles every living entity
 * through `LivingInk`, measures every text box, and builds a `Path` (or a whole taper outline) for each
 * of `udiff.xopp`'s four thousand strokes — four thousand allocations *per frame*, repeated at 60 or
 * 120 frames a second for ink that did not change. Recording the same calls into a picture runs all of
 * that once; a frame then replays the picture, which Skia renders on the GPU.
 *
 * This is deliberately **not** a bitmap. The first attempt rasterised the page into an offscreen image
 * and blitted it, which put every path through Skia's **software** rasteriser instead of the window's
 * GPU surface: the watchdog measured ~780 ms per rebuild on a dense sheet. A picture keeps the drawing
 * instructions, so the replay happens where the window's canvas is — on the GPU.
 *
 * Invalidated by exactly what changes the pixels: the page (an edit), the viewport (scroll, zoom), the
 * window's size, a layer switched off, or another backdrop. A stroke being drawn does **not** invalidate
 * it: that stroke is painted over the picture (`PageCanvas`'s drafts).
 */
internal class PagePicture {

    private var picture: Picture? = null
    private var key: Key? = null
    private val canvasScope = CanvasDrawScope()

    /** How many times the page was recorded — the number a test and the watchdog assert on. */
    var records: Int = 0
        private set

    /** How many frames were served by replaying it. */
    var replays: Int = 0
        private set

    /** Everything that changes the recorded commands. */
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
     * Paint the sheet for this frame: the recorded picture, or a fresh recording.
     *
     * The recording is the canvas's own box, because the page is drawn at the viewport's offset: a page
     * at 400 % is a sheet of twenty screens and only the window's worth of it is recorded.
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
        if (wanted != key) {
            picture?.close()
            picture = record(wanted, measurer)
            key = wanted
            records++
        }
        val sheet = picture ?: return
        target.drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPicture(sheet) }
        replays++
    }

    /** Release the native picture — a canvased page holds a few megabytes of drawing instructions. */
    fun close() {
        picture?.close()
        picture = null
        key = null
    }

    /** The one place the page becomes drawing instructions — the direct paint, into a recorder. */
    private fun record(key: Key, measurer: TextMeasurer): Picture {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.makeWH(key.width.toFloat(), key.height.toFloat()))
        canvasScope.draw(
            density = Density(key.density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas.asComposeCanvas(),
            size = Size(key.width.toFloat(), key.height.toFloat()),
        ) {
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
        return recorder.finishRecordingAsPicture()
    }
}
