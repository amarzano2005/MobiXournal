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
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

/**
 * The page, **recorded once** as a Skia picture and replayed while a stroke is drawn.
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
 * switched off, the paper, or another backdrop. **The viewport does not invalidate it**: the page is
 * recorded at document scale (1 pt = `density` px) and replayed under the canvas's own matrix, so a
 * scroll is a translate and a zoom is a scale — both replays, with no re-record at all
 * (`desktop-dirty-rect-pacing-for-scrolling-and-zooming`). Recording per viewport made every frame of a
 * wheel or pinch pay the full Kotlin paint again, which on `udiff.xopp` was the 3.8 ms a dense page
 * costs.
 *
 * Scaling a *picture* is not resampling: the commands are vectors, so Skia replays them under the total
 * matrix and a glyph or a path is rasterised at the device scale the window actually has. Nothing is
 * softened by the transform — the pixels are the ones the direct paint produces, and `PagePictureTest`
 * measures the difference rather than assuming it.
 *
 * ## Two pictures, because a stroke is drawn one at a time
 *
 * A re-record on every edit is the wrong price for writing: the pen lifts once per letter, and each lift
 * used to throw the sheet away and build it again (3–7 ms on a dense page — a dropped frame every
 * stroke, `desktop-stroke-latency`). So the page is kept as a **base** picture plus a **tail**: the base
 * holds everything as of the last full record, and the tail holds the strokes that were *appended on top
 * of the page's top layer* since then. An append records only the new ink — a stroke or two, a fraction
 * of a millisecond — and leaves the base alone; anything else (an erase, an undo, a reflow, a move)
 * changes ink the base already contains, which a picture cannot take back, so that case re-records the
 * sheet in full and starts a new base.
 *
 * Painting the base and then the tail is the same drawing order the page describes, because the tail is
 * only ever the elements appended to the **last** layer: nothing recorded earlier may paint over them.
 * The two pictures are replayed under one transform into one canvas, so they composite exactly as the
 * single record did (`PagePictureTest` compares both against the direct paint).
 *
 * A tail that grows past [MAX_TAIL_ELEMENTS] folds back into a base, so a long writing run re-records
 * once every few dozen strokes instead of growing an ever longer tail.
 *
 * A stroke being drawn does **not** invalidate either picture: that stroke is painted over them
 * (`PageCanvas`'s drafts).
 */
internal class PagePicture {

    /** Everything as of the last full record — the page's paper, backdrop and ink. */
    private var base: Picture? = null

    /** The strokes appended on top since [base] was recorded, or null when the page is all in [base]. */
    private var tail: Picture? = null

    /** The page [base] is the full record of, and the one the tail's elements were appended to. */
    private var recorded: Page? = null

    /** What [base] depends on besides the page's elements: changing any of it is a full record. */
    private var key: Key? = null

    private val canvasScope = CanvasDrawScope()

    /** How many times the **whole** page was recorded — the number a test and the watchdog assert on. */
    var records: Int = 0
        private set

    /** How many times only the appended strokes were recorded — what a lift costs during writing. */
    var tailRecords: Int = 0
        private set

    /** How many frames were served by replaying it. */
    var replays: Int = 0
        private set

    /**
     * Everything that changes the recorded commands — the viewport is deliberately absent, and so is the
     * ink: the page's elements are compared on their own, because an appended stroke does not need a new
     * base.
     */
    private data class Key(
        val density: Float,
        val backdrop: PageBackdrop?,
        val hiddenLayers: Set<Int>,
        val width: Double,
        val height: Double,
        val background: Background,
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
        val wanted = Key(
            density = density,
            backdrop = backdrop,
            hiddenLayers = hiddenLayers,
            width = page.width,
            height = page.height,
            background = page.background,
        )
        val recordedPage = recorded
        if (wanted != key || recordedPage == null) {
            recordAll(wanted, page, measurer)
        } else if (page !== recordedPage) {
            adopt(page, wanted, measurer)
        }
        val sheet = base ?: return
        target.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(viewport.offsetX * density, viewport.offsetY * density)
            native.scale(zoom, zoom)
            native.drawPicture(sheet)
            tail?.let(native::drawPicture)
            native.restore()
        }
        replays++
    }

    /** Release the native pictures — a canvased page holds a few megabytes of drawing instructions. */
    fun close() {
        base?.close()
        tail?.close()
        base = null
        tail = null
        recorded = null
        key = null
    }

    /**
     * A new page arrived: record only what was appended on top of it, when that is all that changed.
     *
     * The test is on **identity**, not equality: the elements a page edit did not touch are the very
     * objects the recorded page holds, so "same prefix, longer top layer" is a couple of reference
     * comparisons per element and an edit is never mistaken for an append.
     */
    private fun adopt(page: Page, wanted: Key, measurer: TextMeasurer) {
        val previous = recorded ?: return recordAll(wanted, page, measurer)
        val added = appendedElements(previous, page)
        if (added == null || (tailElements + added.size) > MAX_TAIL_ELEMENTS) {
            recordAll(wanted, page, measurer)
            return
        }
        // An append to a hidden top layer changes no pixel: the base stays as it is.
        if (added.isEmpty() || page.layers.lastIndex in wanted.hiddenLayers) {
            recorded = page
            return
        }
        tail?.close()
        tail = record(wanted, page, measurer) { drawElements(added, wanted.density, measurer) }
        tailElements += added.size
        tailRecords++
        recorded = page
    }

    /** The one place the whole page becomes drawing instructions — a full record, and a new base. */
    private fun recordAll(wanted: Key, page: Page, measurer: TextMeasurer) {
        base?.close()
        tail?.close()
        tail = null
        tailElements = 0
        base = record(wanted, page, measurer) {
            drawPageContent(
                page = page,
                pxPerPt = wanted.density,
                textMeasurer = measurer,
                backdrop = wanted.backdrop,
                hiddenLayers = wanted.hiddenLayers,
            )
        }
        recorded = page
        key = wanted
        records++
    }

    /** How many appended elements the current tail holds — the fold trigger. */
    private var tailElements: Int = 0

    /**
     * The page's commands, recorded at **document scale** (one point is `density` pixels) and anchored at
     * its own origin, so the same picture serves every scroll position and every zoom. The cull rect is
     * the page plus a margin: ink can be drawn past the sheet's edge, and a recorder's bounds are what
     * playback is allowed to skip.
     */
    private fun record(wanted: Key, page: Page, measurer: TextMeasurer, body: DrawScope.() -> Unit): Picture {
        val width = (page.width * wanted.density).toFloat()
        val height = (page.height * wanted.density).toFloat()
        val margin = (maxOf(width, height) * CULL_MARGIN).coerceAtLeast(MIN_CULL_MARGIN_PX)
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.makeLTRB(-margin, -margin, width + margin, height + margin))
        canvasScope.draw(
            density = Density(wanted.density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas.asComposeCanvas(),
            size = Size(width, height),
            block = body,
        )
        return recorder.finishRecordingAsPicture()
    }

    private companion object {
        /** How far past the sheet the recording still accepts commands — a fifth of its long side. */
        const val CULL_MARGIN = 0.2f

        /** And never less than this, so a small page's stray ink is still recorded. */
        const val MIN_CULL_MARGIN_PX = 128f

        /**
         * How many appended elements a tail may hold before the sheet is recorded again.
         *
         * Sized to a burst of handwriting: re-recording a dense page costs 3–7 ms, so a fold every few
         * dozen strokes is one hitch in a paragraph instead of one per letter, while the tail itself
         * stays negligible to record and to replay.
         */
        const val MAX_TAIL_ELEMENTS = 48
    }
}

/**
 * The elements [next] appended on top of [previous], or null when the change was not a pure append.
 *
 * Only the **last** layer may grow: the tail is painted after everything the base holds, which is the
 * page's own order only when the new ink sits on top of every other layer. Every other layer must hold
 * the very same element objects, in the same order — a removal there is ink the base still shows, and no
 * picture can take that back.
 *
 * Pure and identity-based, so a test can state exactly which edits are incremental and which are not
 * (`PagePictureTest`).
 */
internal fun appendedElements(previous: Page, next: Page): List<Element>? {
    if (previous.layers.isEmpty() || previous.layers.size != next.layers.size) return null
    val top = previous.layers.lastIndex
    for (index in previous.layers.indices) {
        val old = previous.layers[index].elements
        val fresh = next.layers[index].elements
        if (index == top) {
            if (fresh.size < old.size) return null
        } else if (fresh.size != old.size) {
            return null
        }
        for (position in old.indices) if (old[position] !== fresh[position]) return null
    }
    val old = previous.layers[top].elements
    return next.layers[top].elements.subList(old.size, next.layers[top].elements.size)
}
