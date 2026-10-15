package com.mobixournal.format.model

/**
 * [base] with the pages at [edits] replaced — a **view**, not a copy, so a single-page edit costs one
 * page however long the document is.
 *
 * The document is immutable and every edit hands back a new one (`doc.copy(pages = …)`), which is
 * fine for a `.xopp` whose pages are all in memory but wrong for a document opened page by page: a
 * `toMutableList()` there walks and decrypts every page of a two-hundred-page notebook to change one
 * of them. This list keeps the base — a reader's lazy view, or any other list — and overlays the few
 * indices that moved.
 *
 * Flattened on construction, so editing the same page a thousand times leaves one wrapper and not a
 * thousand: the previous view's own base is reused and its edits are carried over.
 */
internal class EditedPages private constructor(
    private val base: List<Page>,
    private val edits: Map<Int, Page>,
) : AbstractList<Page>() {

    override val size: Int get() = base.size

    override fun get(index: Int): Page = edits[index] ?: base[index]

    /** The pages an edit replaced, by index — what a save compares against the source. */
    val editedIndices: Set<Int> get() = edits.keys

    /**
     * The list this view overlays, edits *not* applied — the reader's own page list when the document
     * was opened page by page (`PageSource`).
     *
     * A caller that has to know where an untouched page came from asks the base: an index this view did
     * not replace is the base's own page, and a base that is a source's view can say which entry those
     * bytes already live in ([MobixPageReuse]).
     */
    val basePages: List<Page> get() = base

    /** A document's own summary: the pages are not printed, they are read. */
    override fun toString(): String = "EditedPages(${edits.size} edited of $size)"

    companion object {
        /** [base] with [index] replaced, reusing an existing view's base rather than stacking one. */
        fun of(base: List<Page>, index: Int, page: Page): List<Page> = if (base is EditedPages) {
            EditedPages(base.base, base.edits + (index to page))
        } else {
            EditedPages(base, mapOf(index to page))
        }
    }
}

/**
 * [document] with its page at [index] replaced by [page] — the one-page form of
 * `document.copy(pages = …)`, which never walks the pages it did not touch.
 *
 * @throws IndexOutOfBoundsException when [index] is not a page of [document].
 */
internal fun Document.withPage(index: Int, page: Page): Document {
    if (index !in pages.indices) {
        throw IndexOutOfBoundsException("page index $index out of bounds (0..${pages.size - 1})")
    }
    return copy(pages = EditedPages.of(pages, index, page))
}
