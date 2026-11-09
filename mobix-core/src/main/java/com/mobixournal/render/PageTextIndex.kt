package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.*

/** Compact searchable page corpus. It retains text geometry, never a source page or its ink. */
class PageTextIndex {
    private class Entry(val revision: Any?, val pdfToken: Any?, val page: Page, val pdf: List<PdfWord>)
    private val entries = HashMap<Int, Entry>()
    private var base: java.lang.ref.WeakReference<List<Page>>? = null
    private var previousEdits: Set<Int> = emptySet()
    private val editedPages = HashMap<Int, java.lang.ref.WeakReference<Page>>()
    private val revisions = HashMap<Int, Long>()
    private var generation = 0L

    @Synchronized fun clear() {
        entries.clear(); editedPages.clear(); revisions.clear()
        base = null; previousEdits = emptySet()
    }

    /** Immutable model overlays identify changed pages without walking the unedited lazy base. */
    @Synchronized
    fun find(doc: Document, pdf: PdfTextIndex?, query: String, handwriting: SearchableHandwriting?,
        checkCancelled: () -> Unit = {}): List<SearchHit> {
        val overlay = doc.pages as? EditedPages
        val nextBase = overlay?.basePages ?: doc.pages
        val nextEdits = overlay?.editedIndices.orEmpty()
        if (base?.get() !== nextBase) {
            entries.clear(); editedPages.clear(); revisions.clear()
            generation++; base = java.lang.ref.WeakReference(nextBase)
        } else for (index in previousEdits + nextEdits) {
            checkCancelled()
            val page = if (index in nextEdits) doc.pages[index] else null
            if (editedPages[index]?.get() !== page) revisions[index] = ++generation
        }
        editedPages.clear()
        for (index in nextEdits) editedPages[index] = java.lang.ref.WeakReference(doc.pages[index])
        previousEdits = nextEdits.toSet()
        return find(PageSource.of(doc.pages), { revisions[it] ?: 0L }, pdf,
            { pdf?.words(it).orEmpty() }, query, handwriting, checkCancelled)
    }

    /** Shell-owned revisions support editable page windows and independently arriving PDF text. */
    @Synchronized
    fun find(source: PageSource, revision: (Int) -> Any?, pdfToken: Any?, pdfWords: (Int) -> List<PdfWord>,
        query: String, handwriting: SearchableHandwriting?, checkCancelled: () -> Unit = {}): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val corpus = object : PageSource {
            override val pageCount: Int get() = source.pageCount
            override fun pageId(index: Int): String? = null
            override fun page(index: Int): Page {
                checkCancelled()
                val version = revision(index)
                val previous = entries[index]
                if (previous != null && previous.revision == version && previous.pdfToken === pdfToken) return previous.page
                val original = source.page(index)
                val compact = original.copy(layers = original.layers.map { layer ->
                    layer.copy(elements = layer.elements.filter { it is TextElement || it is MathBlockElement })
                })
                entries[index] = Entry(version, pdfToken, compact, pdfWords(index).toList())
                return compact
            }
        }
        entries.keys.removeAll { it >= source.pageCount }
        return DocumentSearch.find(corpus, { entries[it]?.pdf.orEmpty() }, query, handwriting, checkCancelled)
    }
}
