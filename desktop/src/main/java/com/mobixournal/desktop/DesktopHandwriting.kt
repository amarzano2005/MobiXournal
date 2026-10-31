package com.mobixournal.desktop

import com.mobixournal.render.HandwrittenWord
import com.mobixournal.render.HandwritingRecognizer
import com.mobixournal.render.InkWordEngine
import com.mobixournal.render.InkEngines
import com.mobixournal.render.SearchableHandwriting

/**
 * The window's handwritten ink, as the shared search engine reads it.
 *
 * The tablet recognises ink with Google ML Kit's digital-ink model, which needs Android; the window answers
 * through [InkEngines.platform] with **Windows' own recognizer** ([WinInkEngine], plugged in at startup by
 * `Main.kt`), and falls back to the pure [com.mobixournal.render.FallbackInkEngine] where the optional
 * handwriting feature is not installed. What is shared is everything around the reading: which strokes form
 * a word, where the word sits, and what a match is.
 *
 * Recognition is **lazy and once per page**: a query reads only the pages it reaches, and a second query on
 * the same document re-asks this cache instead of the engine ([HandwritingCache] throws it away when the
 * document has been edited, because an edited page says something else).
 */
internal class PageHandwriting(
    private val document: DesktopDocument,
    private val engine: InkWordEngine = InkEngines.platform,
) : SearchableHandwriting {

    private val pages = HashMap<Int, List<HandwrittenWord>>()

    /** What page [page] says — read at most once per instance, and never for a page the query never reaches. */
    override fun words(page: Int): List<HandwrittenWord> = pages.getOrPut(page) {
        // A page the source cannot open (a broken part, a page let go behind the walk) reads as empty ink
        // rather than failing the whole query: search must answer what it could read.
        runCatching { HandwritingRecognizer.recognize(document.page(page), engine = engine) }
            .getOrDefault(emptyList())
    }

    override val hasAnyText: Boolean get() = pages.values.any { it.isNotEmpty() }
}

/**
 * One tab's ink reading, kept while the document stays as it was.
 *
 * Recognition is the expensive half of a search — the segmentation walks every stroke of a page — so a
 * query typed letter by letter must not pay for the same page each time. It belongs to the **edits** count,
 * not to the tab: editing a page changes what it says, and a stale word would be a match for ink that is no
 * longer there.
 */
internal class HandwritingCache {

    private var edits = -1
    private var source: PageHandwriting? = null

    fun of(tab: DesktopTab): PageHandwriting {
        val current = tab.document.edits
        val cached = source
        if (cached != null && edits == current) return cached
        return PageHandwriting(tab.document).also {
            source = it
            edits = current
        }
    }
}
