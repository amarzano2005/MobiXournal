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
 * stroke, `desktop-stroke-latency`). So the page is kept as a **base** picture plus **tails**: the base
 * holds everything as of the last full record, and each tail holds the elements **one lift appended on
 * the top of the page's top layer** — one picture per append, in the page's own order.
 *
 * One picture per lift, not one picture re-recorded from the whole run: replaying a run from the base at
 * every lift made the cost of a lift grow with the length of the writing burst (48 strokes of freehand is
 * thousands of segments to re-record), which is exactly the stutter a writer feels between two letters of
 * the same word. A lift now records the one element it added, and painting replays base plus one picture
 * per stroke — the GPU's own work, with no Kotlin paint at all.
 *
 * A run that grows past [MAX_TAIL_ELEMENTS] folds back into a base, so a long writing run re-records once
 * every few pages of handwriting instead of recording an ever longer tail on every lift.
 *
 * A stroke being drawn does **not** invalidate either picture: that stroke is painted over them
 * (`PageCanvas`'s drafts).
 */
internal class PagePicture {

    /** Everything as of the last full record — the page's paper, backdrop and ink. */
    private var base: Picture? = null

    /** One picture per lift since [base], with how many elements it holds. */
    private var tails: MutableList<TailRecord> = mutableListOf()

    /** How many elements the [tails] hold together — the run appended since [base]. */
    private var tailCount: Int = 0

    /**
     * The very element objects the [tails] drew, in the page's own order — the run's identity.
     *
     * Without it the run was only compared against [base], so the elements the tails hold were never
     * checked: a component placed a moment ago lives in a tail, and dragging it left the run the same
     * *length* with the same objects before it. That read as "nothing was appended", the sheet kept the
     * tail's old picture, and the part on screen stayed where it was first placed while the page — and
     * the selection outline around it — moved: exactly the "the pin never attaches" report.
     */
    private var tailElements: List<Element> = emptyList()

    /** The elements one tail picture drew; a lift appends one, a paste may append several. */
    private class TailRecord(val elements: Int, val picture: Picture)

    /** The page [base] is the **full** record of — the line the tail's elements are measured from. */
    private var basePage: Page? = null

    /** The page [base] plus [tail] reflect: the newest page this picture has drawn. */
    private var shown: Page? = null

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
        val current = shown
        if (wanted != key || current == null || base == null) {
            recordAll(wanted, page, measurer)
        } else if (page !== current) {
            adopt(page, wanted, measurer)
        }
        val sheet = base ?: return
        target.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(viewport.offsetX * density, viewport.offsetY * density)
            native.scale(zoom, zoom)
            native.drawPicture(sheet)
            for (tail in tails) native.drawPicture(tail.picture)
            native.restore()
        }
        replays++
    }

    /** Release the native pictures — a canvased page holds a few megabytes of drawing instructions. */
    fun close() {
        base?.close()
        for (tail in tails) tail.picture.close()
        tails.clear()
        tailCount = 0
        base = null
        basePage = null
        shown = null
        key = null
        tailElements = emptyList()
    }

    /**
     * A new page arrived: record only what was appended on top of it, when that is all that changed.
     *
     * The test is on **identity**, not equality: the elements a page edit did not touch are the very
     * objects the base page holds, so "same prefix, longer top layer" is a couple of reference comparisons
     * per element and an edit is never mistaken for an append.
     *
     * The diff is taken against the **base**, not against the previous frame's page: the tail has to hold
     * every element appended since the base was recorded, or each new stroke would replace the tail and the
     * ones before it would vanish from the sheet until something forced a full record. So the tail is
     * recorded again from the whole run each time — a run bounded by [MAX_TAIL_ELEMENTS], so it stays a
     * fraction of a millisecond. The base is the expensive half (paper, ruling, every old stroke, every
     * measured text box) and it is never touched by an append.
     */
    private fun adopt(page: Page, wanted: Key, measurer: TextMeasurer) {
        val base = basePage ?: return recordAll(wanted, page, measurer)
        val added = appendedElements(base, page) ?: return recordAll(wanted, page, measurer)
        // An append to a hidden top layer changes no pixel and complicates the run's bookkeeping for
        // nothing; the key changes the moment the layer comes back, which records the sheet in full.
        if (added.size > MAX_TAIL_ELEMENTS || page.layers.lastIndex in wanted.hiddenLayers) {
            recordAll(wanted, page, measurer)
            return
        }
        // A shorter run is an undo of ink the base never held: drop the pictures it took back.
        if (added.size < tailCount && !dropTailsDown(added.size)) return recordAll(wanted, page, measurer)
        // ...and a run whose elements are not the ones the tails drew is a **modification**, not an append:
        // the tail's picture holds the old ink and cannot be patched, so the sheet is recorded again.
        if (!stillTheSameRun(added)) return recordAll(wanted, page, measurer)
        val fresh = added.subList(tailCount, added.size)
        if (fresh.isNotEmpty()) {
            tails += TailRecord(
                elements = fresh.size,
                picture = record(wanted, page, measurer) { drawElements(fresh, wanted.density, measurer) },
            )
            tailRecords++
        }
        tailCount = added.size
        // The run's own identity, kept for the next frame's check (`stillTheSameRun`).
        tailElements = added.toList()
        shown = page
    }

    /**
     * Whether [added] still starts with the elements the tails drew.
     *
     * Identity, not equality, for the same reason [appendedElements] uses it: an untouched element is the
     * object the base and the tails already hold, so a replaced one is a changed element. A run that is
     * *shorter* than the tails is compared over the part that remains — that is an undo of ink the base
     * never held, which [dropTailsDown] can take back exactly.
     */
    private fun stillTheSameRun(added: List<Element>): Boolean {
        val shared = minOf(tailCount, added.size)
        for (position in 0 until shared) if (tailElements[position] !== added[position]) return false
        return true
    }

    /**
     * Take the run back down to [target] elements, newest picture first.
     *
     * False when a picture would have to be split — one lift's several elements (a paste) cannot be
     * taken back halfway by a picture — and the caller then records the sheet again instead.
     */
    private fun dropTailsDown(target: Int): Boolean {
        var total = tailCount
        while (total > target) {
            val last = tails.lastOrNull() ?: return false
            if (total - last.elements < target) return false
            tails.removeAt(tails.lastIndex).picture.close()
            total -= last.elements
        }
        tailCount = total
        tailElements = tailElements.take(total)
        return true
    }

    /** The one place the whole page becomes drawing instructions — a full record, and a new base. */
    private fun recordAll(wanted: Key, page: Page, measurer: TextMeasurer) {
        base?.close()
        for (tail in tails) tail.picture.close()
        tails.clear()
        tailCount = 0
        tailElements = emptyList()
        base = record(wanted, page, measurer) {
            drawPageContent(
                page = page,
                pxPerPt = wanted.density,
                textMeasurer = measurer,
                backdrop = wanted.backdrop,
                hiddenLayers = wanted.hiddenLayers,
            )
        }
        basePage = page
        shown = page
        key = wanted
        records++
    }

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
         * How many appended elements the tails may hold before the sheet is recorded again.
         *
         * A lift no longer costs the run (one picture per append), so the cap is only about how many
         * pictures a frame replays and how far a fold's 3–7 ms of recording falls from the writer's
         * attention: a pageful of handwriting, not a paragraph.
         */
        const val MAX_TAIL_ELEMENTS = 160
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
