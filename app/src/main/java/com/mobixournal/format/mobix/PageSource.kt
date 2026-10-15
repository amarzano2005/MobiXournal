package com.mobixournal.format.mobix

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import java.io.File

/**
 * A read-only [List] view of a [PageSource]'s pages: `size` is the source's count and `get(i)` the
 * source's own read, so a caller that holds every page as a `List<Page>` — the document model —
 * materialises one only when it indexes it.
 *
 * It is what lets a document opened from a `.mobix` be handed to code written against
 * `Document.pages` unchanged: `pages.size` answers the manifest's count, `pages[12]` opens page 12,
 * and `PageSource.of(pages)` finds its way back to the source ([backing]) so nothing has to carry
 * the two separately.
 */
internal class LazyPages(private val source: PageSource) : AbstractList<Page>() {

    override val size: Int get() = source.pageCount

    override fun get(index: Int): Page {
        // `List.get` owes an index error, not the source's own argument error.
        if (index !in 0 until size) {
            throw IndexOutOfBoundsException("page index $index out of bounds (0..${size - 1})")
        }
        return source.page(index)
    }

    /** The source these pages come from — what [PageSource.of] hands back unchanged. */
    val backing: PageSource get() = source

    /** A page list's own summary: a document of two hundred pages must not print two hundred of them. */
    override fun toString(): String = "LazyPages($size pages)"
}

/**
 * A page's size in pt, and nothing else — what a layout needs of a page to place it *before* anyone
 * draws it. Kept apart from [Page] on purpose: a canvas can then be laid out from the sizes alone,
 * and the page models are read only for the boxes it actually shows.
 */
internal data class PageSize(val widthPt: Double, val heightPt: Double)

/**
 * The page surface of an open document, and the seam that lets a caller work page by page instead
 * of holding every page model at once (`docs/data-path.md` → *The page window*).
 *
 * Two documents can back it:
 *
 * - a **`.mobix`**, through [MobixPagedReader]: [pageCount] and [pageId] are answered from
 *   `manifest.json` alone, and [page] inflates exactly the entry it is asked for — a page's entry, or
 *   the chunk that stands for one in an infinite-canvas document ([infinite-canvas.md](docs/infinite-canvas.md));
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
     * The size of page [index], which a layout places every page by.
     *
     * The default reads the page ([page]), which is all a store that only holds page models can do.
     * A backing store that **declares** the size answers it without touching a page — a paginated
     * `.mobix` says its page size once in `canvas.json` — and that is what lets a canvas lay out a
     * 200-page notebook without opening one of them (`docs/data-path.md` → *The page window*).
     *
     * @throws IllegalArgumentException when [index] is out of range.
     */
    fun pageSize(index: Int): PageSize {
        val page = page(index)
        return PageSize(page.width, page.height)
    }

    /**
     * Id → index over every declared page, built from [pageId] alone and therefore materialising no
     * page. Pages without an id share the empty key, the way an in-memory page list is keyed.
     */
    fun idIndex(): Map<String, Int> = buildMap {
        for (index in 0 until pageCount) put(pageId(index) ?: "", index)
    }

    /**
     * The parts this source's pages **were decoded from**, in index order, or null when its pages are
     * not a document's at all ([MaterializedPages], the canvas's own view of a page list).
     *
     * Only a source that read a `.mobix` can answer it, and the answer is what lets a save tell "this
     * page is still the archive's own" from "this page is the model's" without opening a thing: the
     * part names the entry the page's bytes already live in and the `sha256` those bytes were recorded
     * with ([MobixPageReuse]). Reading it never materialises a page — it is the manifest's own list.
     */
    fun sourceParts(): List<MobixPart>? = null

    /** Release what this source opened itself. The in-memory one opened nothing. */
    override fun close() = Unit

    /**
     * The pages of this source as a `List<Page>` — the shape the document model holds. The list is a
     * view, not a copy: nothing is materialised until an index is read ([LazyPages]).
     */
    fun asPageList(): List<Page> = LazyPages(this)

    companion object {
        /** A view over the pages a [document] already holds in memory. */
        fun of(document: Document): PageSource = of(document.pages)

        /**
         * A view over [pages]: the list itself when it is a source's own view ([LazyPages], so a
         * document opened page by page keeps reading through its reader), a plain view of the list
         * otherwise.
         */
        fun of(pages: List<Page>): PageSource = (pages as? LazyPages)?.backing ?: MaterializedPages(pages)

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

    /**
     * The parts the reader's own manifest lists — its `pages[]`, or its `chunks[]` for an infinite
     * document: the entry and the recorded hash of each, which is what the save's reuse plan matches a
     * page against.
     */
    override fun sourceParts(): List<MobixPart> = reader.parts

    /**
     * The document's declared page size, from `canvas.json` — the one `PAGINATED` mode carries
     * (`docs/infinite-canvas.md`). Falls back to reading the page when the sidecar declares none, so
     * a hand-written or older archive still lays out rather than collapsing to zero-sized pages.
     */
    override fun pageSize(index: Int): PageSize {
        val width = reader.canvas.widthPt
        val height = reader.canvas.heightPt
        if (width > 0.0 && height > 0.0) return PageSize(width, height)
        return super.pageSize(index)
    }

    override fun close() = reader.close()
}
