package com.mobixournal.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mobixournal.format.model.Page
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

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
 * Invalidated by exactly what changes the pixels: the page (an edit), the window's density, a layer
 * switched off, or another backdrop. **The viewport does not invalidate it**: the page is recorded at
 * document scale (1 pt = `density` px) and replayed under the canvas's own matrix, so a scroll is a
 * translate and a zoom is a scale — both replays, with no re-record at all (`desktop-dirty-rect-pacing-
 * for-scrolling-and-zooming`). Recording per viewport made every frame of a wheel or pinch pay the full
 * Kotlin paint again, which on `udiff.xopp` was the 3.8 ms a dense page costs.
 *
 * Scaling a *picture* is not resampling: the commands are vectors, so Skia replays them under the total
 * matrix and a glyph or a path is rasterised at the device scale the window actually has. Nothing is
 * softened by the transform — the pixels are the ones the direct paint produces, and `PagePictureTest`
 * measures the difference rather than assuming it.
 *
 * A stroke being drawn does **not** invalidate it either: that stroke is painted over the picture
 * (`PageCanvas`'s drafts).
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

    /** Everything that changes the recorded commands — the viewport is deliberately absent. */
    private data class Key(
        val page: Page,
        val density: Float,
        val backdrop: PageBackdrop?,
        val hiddenLayers: Set<Int>,
    )

    /**
     * Paint the sheet for this frame: the recorded page, placed by the viewport's own matrix.
     *
     * The recording does not depend on where the reader is looking, which is the whole point: a scroll
     * changes the translate and a zoom changes the scale, and both are applied to the picture instead of
     * to a re-record. The transform is the canvas's, not Kotlin's — recorded coordinates are in px at
     * document scale, so `translate` moves the viewport and `scale` is the zoom.
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
        val wanted = Key(page = page, density = density, backdrop = backdrop, hiddenLayers = hiddenLayers)
        if (wanted != key) {
            picture?.close()
            picture = record(wanted, measurer)
            key = wanted
            records++
        }
        val sheet = picture ?: return
        target.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(viewport.offsetX * density, viewport.offsetY * density)
            native.scale(zoom, zoom)
            native.drawPicture(sheet)
            native.restore()
        }
        replays++
    }

    /** Release the native picture — a canvased page holds a few megabytes of drawing instructions. */
    fun close() {
        picture?.close()
        picture = null
        key = null
    }

    /**
     * The one place the page becomes drawing instructions — the direct paint, into a recorder.
     *
     * The page is recorded at **document scale** (one point is `density` pixels) and anchored at its own
     * origin, so the same picture serves every scroll position and every zoom. The cull rect is the page
     * plus a margin: ink can be drawn past the sheet's edge, and a recorder's bounds are what playback is
     * allowed to skip.
     */
    private fun record(key: Key, measurer: TextMeasurer): Picture {
        val width = (key.page.width * key.density).toFloat()
        val height = (key.page.height * key.density).toFloat()
        val margin = (maxOf(width, height) * CULL_MARGIN).coerceAtLeast(MIN_CULL_MARGIN_PX)
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.makeLTRB(-margin, -margin, width + margin, height + margin))
        canvasScope.draw(
            density = Density(key.density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas.asComposeCanvas(),
            size = Size(width, height),
        ) {
            drawPageContent(
                page = key.page,
                pxPerPt = key.density,
                textMeasurer = measurer,
                backdrop = key.backdrop,
                hiddenLayers = key.hiddenLayers,
            )
        }
        return recorder.finishRecordingAsPicture()
    }

    private companion object {
        /** How far past the sheet the recording still accepts commands — a fifth of its long side. */
        const val CULL_MARGIN = 0.2f

        /** And never less than this, so a small page's stray ink is still recorded. */
        const val MIN_CULL_MARGIN_PX = 128f
    }
}
