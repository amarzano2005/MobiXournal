package com.mobixournal.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.mobixournal.format.model.Page
import java.util.concurrent.Executors

/**
 * Off-screen rasterisation of a page's ink, so panning and flinging blit a bitmap instead of
 * re-submitting every stroke to the canvas each frame.
 *
 * One bitmap per page index, rasterised at a **zoom bucket** rather than the exact on-screen size:
 * buckets step geometrically (see [BUCKET_RATIO]), so the in-between zooms are a scaled blit of at
 * most ~19 % stretch. An entry is dropped when the page's content changes (page identity), when its
 * hidden-layer set changes, or when it leaves the kept window ([retain]).
 *
 * **The raster is produced on a worker, never inside the frame.** Rasterising a page costs tens of
 * milliseconds on a text-heavy sheet, and doing it on the drawing thread is what a fling or a pinch
 * felt like: every page entering the viewport, and every bucket edge a pinch crossed, paused the
 * frame to draw a whole page into a new bitmap. A miss now queues the raster and answers "not
 * cached" for that frame — the caller draws the page's elements directly, which is exact, just not a
 * blit — and [onReady] asks for one more frame once the bitmap exists. The canvas therefore glides
 * at frame rate and the blit takes over a frame or two later.
 *
 * While a pinch moves through buckets the frame keeps **blitting the nearest raster it already
 * has** ([standIn]), up to [STAND_IN_MAX] of upscale, so a pinch never shows a bare redraw either;
 * the worker follows with the sharp one. A pinch that runs on keeps superseding the queued bucket,
 * which is what should happen: only the bucket the fingers come to rest on is worth rasterising.
 *
 * The cache **declines** any page whose bucket would exceed [budgetPx] — at deep zoom a page spans
 * many screens and its full raster would dwarf the screen it feeds. [draw] returns false there and
 * the caller falls back to drawing elements directly, where viewport culling already does the work.
 *
 * Every raster is charged to a shared [BitmapBudget], so this cache and [PdfPageCache] live under
 * one bound instead of two independent guesses; when the total goes over, [trim] hands back the
 * off-screen pages first.
 *
 * In **overview mode** (columns > 1), the cache tightens its bounds: bucket widths are capped and
 * the entry count is limited, so a multi-page grid on a large document can't blow the bitmap budget.
 *
 * **Threading rules** (the same ones [BitmapLruCache] states): [lock] is held only for map
 * operations and is *never* held while rasterising, while blitting or while charging the budget —
 * a charge can call straight back into another cache's [trim], and holding ours across that call
 * inverts the lock order between two caches and deadlocks the drawing thread. [retain] and [clear]
 * recycle bitmaps because they run on the drawing thread, between frames; the worker only ever
 * *publishes* a bitmap and never recycles one, since the frame may still be blitting it.
 */
class InkCache(
    private val budget: BitmapBudget = BitmapBudget.shared,
    private val columns: () -> Int = { 1 },
    /** Invoked on the worker thread when a raster lands — the host asks for one more frame. */
    onReady: () -> Unit = {},
) : BitmapBudget.Client {

    /** Max pixels in one cached page raster, from this device's share of the shared budget. */
    private val budgetPx: Int =
        (budget.perEntryBytes(BitmapLruCache.PAGE_SHARE) / BYTES_PER_PX).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Max entries in overview mode; null in single-page mode (byte budget only). */
    private val maxEntries = { if (columns() > 1) OVERVIEW_MAX_ENTRIES else null }

    /** One cached raster: the bitmap, the page identity it was drawn from, and the hidden-layer set. */
    private class Entry(
        val bitmap: Bitmap,
        val page: Page,
        val hidden: Set<Int>,
        val widthPx: Int,
    )

    /** One page's ink at one bucket, as the worker needs it — everything read off the frame. */
    private class Raster(
        val index: Int,
        val page: Page,
        val hidden: Set<Int>,
        val widthPx: Int,
        val heightPx: Int,
    )

    /** Guards [entries], [retained], [jobs] and [closed]. */
    private val lock = Any()
    private val entries = HashMap<Int, Entry>()

    /**
     * The pages last asked for by [retain] — the window the canvas is showing plus its margin. What
     * [trim] gives back last, and therefore what survives a scroll-back without a re-raster.
     */
    private var retained: Set<Int> = emptySet()

    /** The newest raster wanted per page index; a newer request replaces the queued one. */
    private val jobs = HashMap<Int, Raster>()

    /** What the worker is producing right now, so a repeated request doesn't queue the same work. */
    private val inFlight = HashMap<Int, Raster>()
    private var working = false

    private val worker =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, THREAD_NAME).apply { isDaemon = true } }

    /** Invoked (on the worker thread) whenever a newly rasterised page enters the cache. */
    @Volatile
    var onReady: (() -> Unit)? = onReady

    @Volatile
    private var closed = false

    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    /**
     * The worker's own painters. The drawing thread's are stateful (`Paint`/`Path` fields), so they
     * cannot be shared with a raster running on another thread; a second pair behaves identically
     * and keeps the worker's decoded images and parsed formulas cached across rasters.
     */
    private val workerStrokes = StrokePainter()
    private val workerElements = ElementRenderer(budget)

    init {
        budget.register(this)
    }

    /**
     * Blit page [box]'s ink at its on-screen position, rasterising it in the background if needed.
     * Returns false if this page can't be blitted this frame — no raster yet, or a bucket too large
     * to cache — and the caller must then draw its elements directly.
     */
    internal fun draw(
        canvas: Canvas,
        box: PageBox,
        scrollX: Float,
        scrollY: Float,
        hidden: Set<Int>,
    ): Boolean {
        if (box.widthPx <= 0f || box.heightPx <= 0f) return false
        val bucketW = bucketWidth(box.widthPx) ?: return false
        val bucketH = ((box.heightPx / box.widthPx) * bucketW).toInt().coerceAtLeast(1)
        if (bucketW.toLong() * bucketH > budgetPx) return false
        val page = box.page
        val hit = synchronized(lock) {
            val entry = entries[box.index]
            entry?.takeIf { it.page === page && it.hidden == hidden && inkStandInUsable(it.widthPx, bucketW) }
        }
        if (hit == null) {
            request(box.index, page, hidden, bucketW, bucketH)
            return false
        }
        val left = box.toViewX(0.0, scrollX)
        val top = box.toViewY(0.0, scrollY)
        dst.set(left, top, left + box.widthPx, top + box.heightPx)
        canvas.drawBitmap(hit.bitmap, null, dst, blit)
        // A stand-in is a fine frame and a poor page: ask for the bucket the zoom actually wants.
        if (hit.widthPx != bucketW) request(box.index, page, hidden, bucketW, bucketH)
        return true
    }

    /** Drop every cached page not in [keep] — off-screen pages are the bulk of the memory. */
    fun retain(keep: Set<Int>) {
        var freed = 0L
        synchronized(lock) {
            retained = HashSet(keep)
            val gone = entries.keys.filter { it !in keep }
            for (i in gone) entries.remove(i)?.bitmap?.let { freed += it.byteCount.toLong(); it.recycle() }
        }
        if (freed > 0) budget.credit(freed)
    }

    /** Drop everything (surface teardown, document swap). */
    fun clear() = retain(emptySet())

    /** Stop the worker and release every raster. The view builds a fresh cache with the next view. */
    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            jobs.clear()
            inFlight.clear()
        }
        clear()
        budget.unregister(this)
        // The renderer belongs to the worker: closing it here would recycle bitmaps a raster still in
        // flight may be drawing with, so the worker closes it when it reaches the end of its queue.
        worker.execute { workerElements.close() }
        worker.shutdown()
    }

    /**
     * Give [bytes] back to the shared budget, off-screen pages first. Bitmaps handed back here are
     * **not** recycled: unlike [retain] this can run on another cache's worker thread while the
     * drawing thread still holds the bitmap for the current frame.
     */
    override fun trim(bytes: Long): Long {
        var freed = 0L
        synchronized(lock) {
            for (pass in 0..1) {
                val it = entries.entries.iterator()
                while (freed < bytes && it.hasNext()) {
                    val e = it.next()
                    if (pass == 0 && e.key in retained) continue
                    freed += e.value.bitmap.byteCount.toLong()
                    it.remove()
                }
                if (freed >= bytes) break
            }
        }
        return freed
    }

    // --- the worker --------------------------------------------------------------------------

    /**
     * Queue page [index]'s ink at [widthPx] for background production. The newest request for a page
     * wins, and one already queued — or being produced — is not queued twice: the frame asks again
     * every frame until the raster lands, which during a pinch is many frames in a row.
     */
    private fun request(index: Int, page: Page, hidden: Set<Int>, widthPx: Int, heightPx: Int) {
        synchronized(lock) {
            if (closed) return
            val wanted = jobs[index] ?: inFlight[index]
            if (wanted != null && wanted.page === page && wanted.widthPx == widthPx) return
            jobs[index] = Raster(index, page, HashSet(hidden), widthPx, heightPx)
            if (working) return
            working = true
        }
        // The cache can be closed by the drawing thread between the check above and this call.
        if (runCatching { worker.execute { pump() } }.isFailure) {
            synchronized(lock) { working = false }
        }
    }

    /**
     * Produce the queued rasters until there are none left.
     *
     * One task drains the map rather than one task per request: a pinch queues a raster per bucket
     * it crosses, and a queue of tasks would rasterise every intermediate bucket only to throw it
     * away. Reading the map again after each raster means the worker always picks up the newest
     * request for a page.
     */
    private fun pump() {
        while (true) {
            val job = synchronized(lock) {
                if (closed) {
                    working = false
                    jobs.clear()
                    return
                }
                val next = jobs.values.firstOrNull()
                if (next == null) {
                    working = false
                    return
                }
                jobs.remove(next.index)
                inFlight[next.index] = next
                next
            }
            val fresh = rasterise(job)
            if (fresh == null) {
                synchronized(lock) { inFlight.remove(job.index) }
                continue
            }
            publish(job, fresh)
        }
    }

    /**
     * Put a finished raster in the cache, unless a newer request for the same page has replaced it
     * (its bucket is already the one nobody is asking for).
     */
    private fun publish(job: Raster, entry: Entry) {
        var released = 0L
        var replaced = 0L
        synchronized(lock) {
            inFlight.remove(job.index)
            if (closed) return
            val newer = jobs[job.index]
            if (newer != null && newer.widthPx != job.widthPx) return
            // Overview mode also caps how many previews are held, not only how big they may be.
            val limit = maxEntries()
            if (limit != null && entries.size >= limit && job.index !in entries) {
                val eldest = entries.keys.firstOrNull()
                if (eldest != null) released = entries.remove(eldest)?.bitmap?.byteCount?.toLong() ?: 0L
            }
            replaced = entries.put(job.index, entry)?.bitmap?.byteCount?.toLong() ?: 0L
        }
        // Outside the lock: a charge reaches into every other client's trim (see the class note).
        if (released + replaced > 0) budget.credit(released + replaced)
        budget.charge(this, entry.bitmap.byteCount.toLong())
        onReady?.invoke()
    }

    /** Draw one page into a fresh bitmap. Called on the worker, never holding [lock]. */
    private fun rasterise(job: Raster): Entry? {
        val bitmap = try {
            Bitmap.createBitmap(job.widthPx, job.heightPx, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return null
        }
        val scale = (job.widthPx / job.page.width).toFloat()
        PageRenderer.drawElements(
            Canvas(bitmap), job.page, scale, 0f, 0f, workerStrokes, workerElements, job.hidden,
        )
        return Entry(bitmap, job.page, job.hidden, job.widthPx)
    }

    /**
     * The smallest bucket width at or above [widthPx], or null once that would blow the budget on
     * width alone. Buckets start at [BUCKET_BASE] and step by [BUCKET_RATIO]. In overview mode
     * (columns > 1), the width is capped to keep memory bounded across many visible pages.
     */
    private fun bucketWidth(widthPx: Float): Int? {
        var w = BUCKET_BASE.toFloat()
        val cap = if (columns() > 1) OVERVIEW_BUCKET_CAP.toFloat() else Float.POSITIVE_INFINITY
        while (w < widthPx) {
            w *= BUCKET_RATIO
            if (w > budgetPx || w > cap) return null
        }
        return w.toInt().coerceAtMost(cap.toInt())
    }

    private companion object {
        const val THREAD_NAME = "ink-raster"
        const val BYTES_PER_PX = 4

        /** Narrowest bucket; below this a page is a thumbnail and the raster is nearly free. */
        const val BUCKET_BASE = 256

        /** Geometric step between buckets: ≤19% upscale between rasterisations. */
        const val BUCKET_RATIO = 1.19f

        /** Max bucket width in overview mode (columns > 1), keeping previews memory-bounded. */
        private const val OVERVIEW_BUCKET_CAP = 200

        /** Max cached entries in overview mode; single-page mode is byte-budget only. */
        private const val OVERVIEW_MAX_ENTRIES = 12
    }
}

/**
 * How many pages either side of the viewport keep their ink raster alive.
 *
 * The margin is what makes a scroll *reversible*: the canvas used to retain only the pages on
 * screen, so the page a flick had just left was recycled on the very next frame and flicking back
 * paid a full re-raster for a page that had been drawn a moment ago. One page either side covers a
 * reading-speed scroll without holding a document's worth of bitmaps — the shared budget still
 * evicts them first ([InkCache.trim]).
 */
internal const val INK_RETAIN_MARGIN = 1

/** The on-screen pages plus [margin] either side, as the set [InkCache.retain] keeps. */
internal fun retainedAround(
    visible: List<PageBox>,
    pageCount: Int,
    margin: Int = INK_RETAIN_MARGIN,
): Set<Int> {
    if (visible.isEmpty() || pageCount <= 0) return emptySet()
    val first = (visible.first().index - margin).coerceAtLeast(0)
    val last = (visible.last().index + margin).coerceAtMost(pageCount - 1)
    if (last < first) return emptySet()
    return (first..last).toHashSet()
}

/** How far past its own width a cached raster may be stretched before the ink would visibly blur. */
internal const val INK_STAND_IN_MAX = 1.45f

/**
 * Whether a raster of [cachedWidth] is a presentable stand-in for a page now drawn at [wantedWidth].
 *
 * Upscaling is the only thing that costs quality, so the bound is on that direction: a raster may
 * stand in while it is stretched by at most [INK_STAND_IN_MAX] (a little past the ~19 % a bucket
 * already implies), and any raster **wider** than the wanted bucket qualifies, because shrinking a
 * sharper bitmap is free. Past the bound the ink would visibly blur, so the caller draws the
 * elements instead and waits for the worker.
 */
internal fun inkStandInUsable(cachedWidth: Int, wantedWidth: Int): Boolean {
    if (cachedWidth <= 0 || wantedWidth <= 0) return false
    return wantedWidth <= cachedWidth || wantedWidth <= cachedWidth * INK_STAND_IN_MAX
}
