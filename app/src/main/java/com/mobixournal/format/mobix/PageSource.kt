package com.mobixournal.format.mobix

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import java.io.File

/**
 * The page surface of an open document, and the seam that lets a caller work page by page instead
 * of holding every page model at once (`docs/data-path.md` → *The page window*).
 *
 * Two documents can back it:
 *
 * - a **`.mobix`**, through [MobixPagedReader]: [pageCount] and [pageId] are answered from
 *   `manifest.json` alone, and [page] inflates exactly the entry it is asked for;
 * - **pages already in memory** — a `.xopp`, or a document that was just created — where the
 *   surface is a thin view of the list the caller already holds.
 *
 * A source never decides *when* a page is released: the backing reader's cache window does that for
 * a `.mobix`, and the caller owns the list for an in-memory document. Closing a source only releases
 * what it opened itself, so a caller that did not open the archive must not close over it: the
 * `.mobix`-backed source closes its whole reader (and its entries) with it.
 */
internal interface PageSource : AutoCloseable {

    /** How many pages the document declares — a `.mobix` answers this from its manifest. */
    val pageCount: Int

    /**
     * The stable id of page [index] — the manifest part id for a `.mobix` — or null when [index] is
     * out of range. Reading an id never materialises the page.
     */
    fun pageId(index: Int): String?

    /**
     * The page model at [index], materialised on demand.
     *
     * @throws IllegalArgumentException when [index] is out of range.
     */
    fun page(index: Int): Page

    /**
     * Id → index over every declared page, built from [pageId] alone and therefore materialising no
     * page. Pages without an id share the empty key, the way an in-memory page list is keyed.
     */
    fun idIndex(): Map<String, Int> = buildMap {
        for (index in 0 until pageCount) put(pageId(index) ?: "", index)
    }

    /** Release what this source opened itself. The in-memory one opened nothing. */
    override fun close() = Unit

    companion object {
        /** A view over the pages a [document] already holds in memory. */
        fun of(document: Document): PageSource = of(document.pages)

        /** A view over [pages] that are already in memory. */
        fun of(pages: List<Page>): PageSource = MaterializedPages(pages)

        /** The pages of an already-open `.mobix` [reader], counted and id'd from its manifest. */
        fun of(reader: MobixPagedReader): PageSource = PagedMobix(reader)

        /** Open a `.mobix` file and read it page by page; closing the source closes the file. */
        fun open(
            file: File,
            cacheBudget: Int = MobixPagedReader.DEFAULT_CACHE_BUDGET,
        ): PageSource = of(MobixPagedReader.open(file, cacheBudget))
    }
}

/** A [PageSource] over pages the caller already holds; it materialises nothing and owns nothing. */
private class MaterializedPages(private val pages: List<Page>) : PageSource {

    override val pageCount: Int get() = pages.size

    override fun pageId(index: Int): String? = pages.getOrNull(index)?.id

    override fun page(index: Int): Page {
        require(index in pages.indices) { "page index $index out of bounds (0..${pages.size - 1})" }
        return pages[index]
    }
}

/** A [PageSource] over a paged `.mobix`, delegating counting, ids and materialisation to [reader]. */
private class PagedMobix(private val reader: MobixPagedReader) : PageSource {

    override val pageCount: Int get() = reader.pageCount

    override fun pageId(index: Int): String? = reader.pageId(index)

    override fun page(index: Int): Page = reader.getPage(index)

    override fun close() = reader.close()
}
