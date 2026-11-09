package com.mobixournal.desktop.ink
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.shell.DesktopTab

import com.mobixournal.render.HandwrittenWord
import com.mobixournal.render.HandwritingRecognizer
import com.mobixournal.render.InkEngines
import com.mobixournal.render.InkLine
import com.mobixournal.render.InkWordEngine
import com.mobixournal.render.SearchableHandwriting
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The window's handwritten ink, as the shared search engine reads it.
 *
 * The tablet recognises ink with Google ML Kit's digital-ink model, which needs Android; the window answers
 * through [InkEngines.platform] with **Windows' own recognizer** ([WinInkEngine], plugged in at startup by
 * `Main.kt`), and falls back to the pure [com.mobixournal.render.FallbackInkEngine] where the optional
 * handwriting feature is not installed. What is shared is everything around the reading: which strokes form
 * a sentence's line, where a word sits, and what a match is.
 *
 * Recognition is **lazy and per line**: a query reads only the pages it reaches, and inside a page only the
 * lines it has no reading for. A line's reading is kept against [InkLine.key] — a structural hash of that
 * line's own strokes — so an edit re-reads the line the pen touched and leaves every other line on the page
 * as read. A page recognised once used to be thrown away wholesale on any edit; on a written sheet that
 * meant re-reading thirty lines because one word moved.
 */
internal class PageHandwriting(
    private val document: DesktopDocument,
    private val engine: InkWordEngine = InkEngines.platform,
) : SearchableHandwriting {

    private class Reading(
        val revision: Pair<Long, Long>,
        val lines: Map<String, List<HandwrittenWord>>,
        val words: List<HandwrittenWord>,
    )
    private val readings = HashMap<Int, Reading>()

    /** Recognition results are a compact per-page text index, not a 128-line working cache. */
    @Synchronized override fun words(page: Int): List<HandwrittenWord> {
        if (page !in 0 until document.pageCount) return emptyList()
        val revision = document.pageRevision(page)
        val previous = readings[page]
        if (previous?.revision == revision) return previous.words
        val model = runCatching { document.page(page) }.getOrNull() ?: return emptyList()
        val lines = HandwritingRecognizer.lines(model)
        val recognized = LinkedHashMap<String, List<HandwrittenWord>>()
        for (line in lines) {
            val key = lineFingerprint(line)
            recognized[key] = previous?.lines?.get(key)
                ?: runCatching { HandwritingRecognizer.recognize(line, engine) }.getOrDefault(emptyList())
        }
        val words = recognized.values.flatten()
        if (document.pageRevision(page) == revision) readings[page] = Reading(revision, recognized, words)
        readings.keys.removeAll { it >= document.pageCount }
        return words
    }

    @get:Synchronized override val hasAnyText: Boolean get() = readings.values.any { it.words.isNotEmpty() }

}

/**
 * One tab's ink reading, kept while the document stays the same **object**.
 *
 * Recognition is the expensive half of a search, so a query typed letter by letter must not pay for the
 * same ink twice. What decides whether a reading is still true is the ink, not the edit count: [PageHandwriting]
 * keys every reading on the line it read (a geometry fingerprint), so an edit is answered by re-reading the line that
 * changed instead of dropping the page. The cache is therefore rebuilt only when the tab points at another
 * document — a Save As, a reload, a recovered file.
 */
internal class HandwritingCache {

    private var document: DesktopDocument? = null
    private var source: PageHandwriting? = null

    fun of(tab: DesktopTab): PageHandwriting {
        val current = tab.document
        val cached = source
        if (cached != null && document === current) return cached
        return PageHandwriting(current).also {
            source = it
            document = current
        }
    }
}

/**
 * Reads the page in front of the reader **about 300 ms after the hand settles**, on a thread of its own.
 *
 * The point is what a search no longer pays: the line just written is already read when a query arrives,
 * and a page turn reads the page being turned instead of the page being searched. One job at a time — a
 * later call replaces the pending one, so a pen that keeps writing costs nothing beyond the debounce — and
 * the thread is a daemon at minimum priority, because ink being read must never compete with ink being drawn.
 *
 * The read itself is safe off the UI thread by the same rule search already follows: pages are read through
 * the document's window, which is what `SearchRunner` does on `Dispatchers.Default`.
 */
internal object InkReading {

    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ink-reading").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    private var pending: ScheduledFuture<*>? = null

    /** Read [page] of [handwriting] once nothing newer has been asked for [delayMs] milliseconds. */
    @Synchronized
    fun schedule(handwriting: PageHandwriting, page: Int, delayMs: Long = WARM_DELAY_MS) {
        pending?.cancel(false)
        pending = worker.schedule({ runCatching { handwriting.words(page) } }, delayMs, TimeUnit.MILLISECONDS)
    }
}

/** Read the page this tab is showing, a moment after the call — a lift, a page turn, an open. */
internal fun DesktopTab.warmInk() {
    val reader = handwriting.of(this)
    InkReading.schedule(reader, page)
}

/** The pause after the last stroke (or page turn) before the page is read. */
private const val WARM_DELAY_MS = 300L
