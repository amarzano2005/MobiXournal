/**
 * The canvas's **search**: the query, its hits, the handwriting index behind them and the one worker
 * thread that builds that index.
 *
 * Search crosses two clocks. The textual half — authored text boxes and an imported PDF's text layer —
 * is answered by a separate query worker through [DocumentSearch.find]; the handwritten half needs the recogniser,
 * which on a real notebook takes seconds, so it is built on a **background thread** and its result lands
 * back on the main looper. Between the two sits a *generation* counter: every cancel, reload or newer
 * request bumps it, and a result posted from a dead generation is dropped rather than installed over a
 * document it no longer describes.
 *
 * That state — the query, the hits, the current hit, the index, the document it was built for, the
 * generation and the two executors — used to be ten members of `DrawingSurfaceView`, reachable from
 * every `DrawingSurface*.kt` extension and reachable only through them. It is one subject, so it lives
 * here: the canvas hands in four readers (the document, its PDF text layer, how to repaint, how to
 * reveal a hit) and keeps nothing of it. The executors are injectable, so [CanvasSearchTest] drives
 * indexing, cancellation and navigation synchronously with a direct executor and two documents.
 *
 * One deliberate difference from a plain state holder: the object **owns its worker thread**. A
 * search index is a job, not a value, and a job that can outlive its owner by a document generation is
 * exactly why the generation counter exists.
 */
package com.mobixournal.render

import com.mobixournal.format.model.Document
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong

internal class CanvasSearch(
    /** The document to search — read afresh on each query, because the canvas's `doc` is replaced. */
    private val document: () -> Document,
    /** The imported PDF's text layer, or null when the document has no PDF behind it. */
    private val pdfText: () -> PdfTextIndex?,
    /** Notified with the count and the current position, so the top bar can show navigation state. */
    private val onChanged: (SearchStatus) -> Unit = {},
    /** Notified when indexing starts or ends (true = indexing), with optional progress text. */
    private val onIndexing: (Boolean, String?) -> Unit = { _, _ -> },
    /** Repaint, because the highlights changed. */
    private val onRepaint: () -> Unit = {},
    /** Bring a hit under the eye — the canvas is the only thing that knows where a page is. */
    private val reveal: (SearchHit) -> Unit = {},
    /** Where the recogniser runs: one background thread by default, [Runnable.run] in a test. */
    private val background: Executor = indexingExecutor(),
    /** Where a finished index lands: the main looper by default, direct in a test. */
    private val main: Executor = mainThreadExecutor(),
    private val queries: Executor = searchQueryExecutor(),
) {

    /** The query in force, as typed. */
    var query = ""
        private set

    /** Every hit of [query], in document order. */
    var hits: List<SearchHit> = emptyList()
        private set

    /** The index into [hits] the user is standing on, or -1 when nothing is current. */
    var current = -1
        private set

    /** The handwriting index, once built. Null while it is missing — [rebuild] then starts building it. */
    var handwriting: HandwritingIndex? = null
        private set

    /** The document [handwriting] was built for — the identity check that avoids a second run. */
    private var indexedDoc: Document? = null

    /** True while the recogniser is running, so a keystroke does not start a second run. */
    private var indexing = false

    /**
     * Bumped by every cancel, reload and new job. A result carries the generation it was built under, and
     * the main thread installs it only while that is still the live one.
     */
    @Volatile private var generation = 0
    private val queryGeneration = AtomicLong()
    private var textIndex = PageTextIndex()

    /** Run [query] and repaint: what the search field calls on every keystroke. */
    fun setQuery(value: String): SearchStatus {
        query = value
        rebuild()
        onRepaint()
        return status()
    }

    /** True when a query can be answered without waiting for the recogniser. */
    fun isReady(): Boolean = handwriting != null || DocumentSearch.totalStrokeCount(document()) == 0

    /** The current count and position, as the top bar shows it. */
    fun status(): SearchStatus =
        SearchStatus(current = if (current >= 0) current + 1 else 0, total = hits.size)

    /**
     * Make sure the handwriting index exists, then call [onComplete] — on the main thread, after any
     * build that had to run. The a-priori "nothing to index" case answers immediately.
     */
    fun ensureIndex(onComplete: (() -> Unit)? = null) {
        val target = document()
        if (handwriting != null || DocumentSearch.totalStrokeCount(target) == 0) {
            if (handwriting == null) handwriting = HandwritingIndex(emptyList())
            onIndexing(false, null)
            onComplete?.invoke()
            return
        }
        indexedDoc = target
        indexing = true
        val taskGeneration = ++generation
        onIndexing(true, STARTING)
        background.execute {
            try {
                val totalPages = target.pages.size
                val found = HandwritingIndex.build(
                    doc = target,
                    onProgress = { page, total ->
                        if (generation == taskGeneration) {
                            main.execute {
                                if (generation == taskGeneration) {
                                    onIndexing(true, progressLine(page, total, totalPages))
                                }
                            }
                        }
                    },
                    isCancelled = { generation != taskGeneration },
                )
                main.execute {
                    if (generation == taskGeneration) {
                        handwriting = found
                        indexing = false
                        onIndexing(false, null)
                        if (query.isNotEmpty()) {
                            rebuild()
                            onRepaint()
                        }
                        onComplete?.invoke()
                    }
                }
            } catch (_: Exception) {
                main.execute {
                    if (generation == taskGeneration) {
                        indexing = false
                        onIndexing(false, null)
                    }
                }
            }
        }
    }

    /** Step to the next hit, wrapping; an unindexed document starts building its index first. */
    fun next(): SearchStatus {
        if (hits.isEmpty() && query.isNotEmpty() && handwriting == null && !indexing) ensureIndex()
        if (hits.isEmpty()) return status()
        current = (current + 1).floorMod(hits.size)
        hits.getOrNull(current)?.let(reveal)
        return notifyChanged()
    }

    /** Step to the previous hit, wrapping; the same first-run rule as [next]. */
    fun previous(): SearchStatus {
        if (hits.isEmpty() && query.isNotEmpty() && handwriting == null && !indexing) ensureIndex()
        if (hits.isEmpty()) return status()
        current = (current - 1).floorMod(hits.size)
        hits.getOrNull(current)?.let(reveal)
        return notifyChanged()
    }

    /** Forget the query and its hits, repainting the highlights away. */
    fun clear(): SearchStatus {
        queryGeneration.incrementAndGet()
        query = ""
        hits = emptyList()
        current = -1
        onRepaint()
        return notifyChanged()
    }

    /**
     * A document has replaced the one this index describes: drop the hits and the index, say so, and
     * start indexing the new document at once when a query is waiting for it.
     */
    fun reload() {
        cancel()
        forget()
        notifyChanged()
        if (query.isNotEmpty()) startIndexing(document())
    }

    /**
     * The recogniser itself changed (its model became available): the index is void although the
     * document is not, so rebuild it in place when a query is waiting.
     */
    fun invalidate() {
        cancel()
        handwriting = null
        indexedDoc = null
        if (query.isNotEmpty()) startIndexing(document())
    }

    /**
     * The document was edited, so the index no longer describes it: void the index and, unless the edit
     * is still in progress ([midGesture] — a live move rewrites the page every frame), requery.
     */
    fun edited(midGesture: Boolean) {
        generation++
        queryGeneration.incrementAndGet()
        indexing = false
        handwriting = null
        indexedDoc = null
        if (query.isNotEmpty() && !midGesture) rebuild()
    }

    /** Drop any in-flight indexing; its posted result will be ignored by its generation. */
    fun cancel() {
        generation++
        queryGeneration.incrementAndGet()
        indexing = false
        onIndexing(false, null)
    }

    /** Recompute the hits of the query in force — after an edit, a PDF text layer arriving, or a query. */
    fun rebuild() {
        val token = queryGeneration.incrementAndGet()
        if (query.isEmpty()) {
            // An empty query clears the highlights, not the index: the next query would rebuild it.
            hits = emptyList()
            current = -1
            notifyChanged()
            return
        }
        if (handwriting == null && !indexing) startIndexing(document())
        val target = document()
        val text = pdfText()
        val requested = query
        val ink = handwriting
        val index = textIndex
        hits = emptyList()
        current = -1
        notifyChanged()
        queries.execute {
            try {
                val found = index.find(target, text, requested, ink) {
                    if (queryGeneration.get() != token) throw CancellationException()
                }
                main.execute {
                    if (queryGeneration.get() == token && document() === target && query == requested) {
                        hits = found
                        current = when {
                            hits.isEmpty() -> -1
                            current < 0 -> 0
                            else -> current.coerceAtMost(hits.lastIndex)
                        }
                        notifyChanged()
                        onRepaint()
                    }
                }
            } catch (_: CancellationException) {
                // A newer query owns the result.
            }
        }
    }

    /** Drop the hits and the index, without touching the query. */
    private fun forget() {
        textIndex = PageTextIndex()
        hits = emptyList()
        current = -1
        handwriting = null
        indexedDoc = null
    }

    /**
     * Build the index for [target] in the background, unless it is already there for that very document
     * or a run is in flight. Results and progress arrive on the main thread, guarded by the generation.
     */
    private fun startIndexing(target: Document) {
        if (indexedDoc === target && (handwriting != null || indexing)) return
        indexedDoc = target
        indexing = true
        val taskGeneration = ++generation
        background.execute {
            try {
                val found = HandwritingIndex.build(target, isCancelled = { generation != taskGeneration })
                main.execute {
                    if (generation == taskGeneration) {
                        handwriting = found
                        indexing = false
                        if (query.isNotEmpty()) {
                            rebuild()
                            onRepaint()
                        }
                    }
                }
            } catch (_: Exception) {
                main.execute { if (generation == taskGeneration) indexing = false }
            }
        }
    }

    private fun notifyChanged(): SearchStatus = status().also(onChanged)

    /** The progress line a multi-page build shows; one page is simply "Processing text…". */
    private fun progressLine(page: Int, total: Int, totalPages: Int): String =
        if (totalPages > 1) "Processing page $page of $total…" else PROCESSING

    private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod

    private companion object {
        const val STARTING = "Starting text recognition…"
        const val PROCESSING = "Processing text…"
    }
}

/**
 * The single daemon thread handwriting recognition runs on, at background priority: a notebook must not
 * compete with the pen for the main thread, and the indexer must not outlive the app.
 */
private fun indexingExecutor(): Executor = Executors.newSingleThreadExecutor { runnable ->
    Thread({
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        } catch (_: Throwable) {
        }
        runnable.run()
    }, "handwriting-index").apply { isDaemon = true }
}

/** Post to the main looper — where the canvas, and therefore the index, is allowed to be installed. */
private fun mainThreadExecutor(): Executor {
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    return Executor { runnable -> handler.post(runnable) }
}

/** A single query worker with one replaceable debounce slot. */
private fun searchQueryExecutor(): Executor {
    val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "document-search").apply { isDaemon = true }
    }
    var pending: java.util.concurrent.ScheduledFuture<*>? = null
    val lock = Any()
    return Executor { task ->
        synchronized(lock) {
            pending?.cancel(false)
            pending = worker.schedule(task, 150, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }
}
