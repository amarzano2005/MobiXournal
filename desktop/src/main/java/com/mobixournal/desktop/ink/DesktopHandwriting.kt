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

    /** One reading per line of ink, keyed by the line's own content ([InkLine.key]). */
    private val readings = HashMap<Int, List<HandwrittenWord>>()

    /** What page [page] says — every line read at most once per instance, no page the query never reaches. */
    override fun words(page: Int): List<HandwrittenWord> {
        // A page the source cannot open (a broken part, a page let go behind the walk) reads as empty ink
        // rather than failing the whole query: search must answer what it could read.
        val lines = runCatching { HandwritingRecognizer.lines(document.page(page)) }.getOrDefault(emptyList())
        if (lines.isEmpty()) return emptyList()
        return lines.flatMap { line -> read(line) }
    }

    /** [line]'s reading, from the cache when its ink has not changed since the last one. */
    private fun read(line: InkLine): List<HandwrittenWord> {
        readings[line.key]?.let { return it }
        // A page being written all afternoon mints a key per version of the line under the pen; the map is
        // a cache, not a history, so it is emptied rather than grown without bound.
        if (readings.size >= MAX_LINE_READINGS) readings.clear()
        val words = runCatching { HandwritingRecognizer.recognize(line, engine) }.getOrDefault(emptyList())
        readings[line.key] = words
        return words
    }

    override val hasAnyText: Boolean get() = readings.values.any { it.isNotEmpty() }
}

/**
 * One tab's ink reading, kept while the document stays the same **object**.
 *
 * Recognition is the expensive half of a search, so a query typed letter by letter must not pay for the
 * same ink twice. What decides whether a reading is still true is the ink, not the edit count: [PageHandwriting]
 * keys every reading on the line it read ([InkLine.key]), so an edit is answered by re-reading the line that
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

/** How many line readings one page keeps before the cache starts over. */
private const val MAX_LINE_READINGS = 128

/** The pause after the last stroke (or page turn) before the page is read. */
private const val WARM_DELAY_MS = 300L
