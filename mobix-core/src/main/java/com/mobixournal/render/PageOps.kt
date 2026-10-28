package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page

/**
 * Pure page-list edits for the editor: insert and delete pages. Kept Android-free so the
 * document-structure logic is unit-testable off-device **and shared by both shells** — the tablet's
 * page commands and the window's Pages menu add, remove and duplicate through this one file, so the
 * two cannot disagree about what a new page inherits (see `docs/pages-and-paper.md`). A new page is
 * born at the **default page size** the caller passes in (Settings ▸ Editor), keeping the **paper
 * ruling** of the page it sits beside, with a single empty layer — but never its content: a
 * `pdf`/`pixmap` background is dropped to a plain white sheet so the fresh page can't re-show the
 * source page's PDF/image underneath (which read as a duplicate).
 */
object PageOps {

    /** A plain white blank page's fill colour (ARGB). */
    private const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * A copy of [pages] with a fresh blank page of [widthPt]×[heightPt] inserted after [index]. Empty
     * input is returned as-is.
     */
    fun addAfter(pages: List<Page>, index: Int, widthPt: Double, heightPt: Double): List<Page> {
        if (pages.isEmpty()) return pages
        val i = index.coerceIn(0, pages.lastIndex)
        return insertedAt(pages, i + 1, freshPage(pages[i], widthPt, heightPt))
    }

    /**
     * A copy of [pages] with a fresh blank page of [widthPt]×[heightPt] inserted *before* [index].
     * Empty input is returned as-is.
     */
    fun addBefore(pages: List<Page>, index: Int, widthPt: Double, heightPt: Double): List<Page> {
        if (pages.isEmpty()) return pages
        val i = index.coerceIn(0, pages.lastIndex)
        return insertedAt(pages, i, freshPage(pages[i], widthPt, heightPt))
    }

    /**
     * A copy of [pages] with page [index] duplicated straight after itself — content and all, unlike
     * [addAfter]. The background is kept verbatim, so a duplicated PDF page still shows its source.
     */
    fun duplicateAt(pages: List<Page>, index: Int): List<Page> {
        if (pages.isEmpty()) return pages
        val i = index.coerceIn(0, pages.lastIndex)
        return pages.toMutableList().apply { add(i + 1, pages[i].copy()) }
    }

    /**
     * A fresh empty sheet in the given size, sitting beside [near]: it keeps [near]'s paper ruling
     * only. A [Background.Solid] carries just that ruling (no content), so it's kept as-is for
     * continuity; a `pdf`/`pixmap` background would re-show the source page's content, so it's
     * replaced with a plain white sheet.
     */
    private fun freshPage(near: Page, widthPt: Double, heightPt: Double): Page {
        val background = when (val source = near.background) {
            is Background.Solid -> source
            else -> Background.Solid(WHITE, "plain")
        }
        return near.copy(
            width = widthPt,
            height = heightPt,
            background = background,
            layers = listOf(Layer(emptyList())),
        )
    }

    /**
     * [pages] with [added] appended after them — the "Append" half of PDF import. A brand-new
     * document is a single empty sheet nobody has drawn on; keeping it would leave a stray blank page
     * in front of the imported PDF, so that one case is dropped and the appended pages stand alone.
     */
    fun appendPages(pages: List<Page>, added: List<Page>): List<Page> {
        if (added.isEmpty()) return pages
        if (pages.size == 1 && isPristineBlank(pages[0])) return added
        return pages + added
    }

    /** True for an untouched blank sheet: a solid background and not a single element on any layer. */
    private fun isPristineBlank(page: Page): Boolean =
        page.background is Background.Solid && page.layers.all { it.elements.isEmpty() }

    /**
     * A copy of [pages] with the page at [from] lifted out and re-inserted at [to] — the page-overview
     * drag-to-reorder edit. Indices are positions in the *result*, so moving page 0 to 2 in a 4-page
     * document yields `1,2,0,3`. An out-of-range [from] or a no-op move returns the list unchanged, so
     * callers can hand the result straight to an "only if it differs" edit.
     */
    fun move(pages: List<Page>, from: Int, to: Int): List<Page> {
        if (from !in pages.indices) return pages
        val target = to.coerceIn(0, pages.lastIndex)
        if (target == from) return pages
        return pages.toMutableList().apply { add(target, removeAt(from)) }
    }

    /**
     * A copy of [pages] with the run `[first]..[last]` lifted out and re-inserted so that its **first** page
     * lands at [target] — moving a range of pages at once, rather than one at a time.
     *
     * [target] is a position in the *result*, as [move]'s is: with `A B C D`, moving `B C` to 0 yields
     * `B C A D` and moving it to 3 yields `A D B C`. A target inside the range is a no-op (the run is
     * already where it was asked to go), out-of-range arguments are clamped, and a refusal returns the
     * same instance so callers can hand the result straight to an "only if it differs" edit.
     */
    fun moveRange(pages: List<Page>, first: Int, last: Int, target: Int): List<Page> {
        if (pages.isEmpty()) return pages
        val from = first.coerceIn(0, pages.lastIndex)
        val to = last.coerceIn(from, pages.lastIndex)
        val at = target.coerceIn(0, pages.lastIndex)
        if (at in from..to) return pages
        val block = pages.subList(from, to + 1)
        val rest = pages.filterIndexed { index, _ -> index !in from..to }
        return rest.toMutableList().apply { addAll(at.coerceAtMost(rest.size), block) }
    }

    /**
     * A copy of [pages] with every page in [indices] lifted out and re-inserted, in document order, so that
     * the **first** lifted page lands at [target] — moving a scattered selection as one block.
     *
     * [target] is a position in the *result*, as [move]'s and [moveRange]'s are. An empty selection, a
     * selection covering every page, or a target that clamps onto where the block already starts returns the
     * same instance, so callers can hand the result straight to an "only if it differs" edit.
     */
    fun moveAll(pages: List<Page>, indices: Set<Int>, target: Int): List<Page> {
        val lift = indices.filter { it in pages.indices }.toSet()
        if (lift.isEmpty() || lift.size >= pages.size) return pages
        val ordered = lift.sorted()
        val rest = pages.filterIndexed { index, _ -> index !in lift }
        val at = target.coerceIn(0, rest.size)
        // Already there: a contiguous block whose first page stays where it starts. Compared by index rather
        // than by the rebuilt list, so a 200-page notebook is never walked to answer "did anything move?".
        val contiguous = ordered.withIndex().all { (position, index) -> index == ordered.first() + position }
        if (contiguous && at == ordered.first()) return pages
        val block = ordered.map { pages[it] }
        return rest.toMutableList().apply { addAll(at, block) }
    }

    /**
     * The pages at [indices], in ascending document order — the page-overview copy. Out-of-range
     * indices are ignored, so an empty or fully out-of-range selection yields an empty list. Pages are
     * immutable, so the "copy" shares structure with the originals and is safe to re-insert as-is:
     * strokes, layers, page size, background and any PDF/image reference all come along verbatim and
     * are written out again by the `.xopp` writer, so the duplicate round-trips.
     */
    fun copyOf(pages: List<Page>, indices: Set<Int>): List<Page> =
        indices.filter { it in pages.indices }.sorted().map { pages[it] }

    /**
     * A copy of [pages] with [added] inserted directly *after* index [after]; a negative [after] puts
     * them in front of the document. [after] is clamped into range, and an empty [added] returns the
     * same instance so callers can hand the result straight to an "only if it differs" edit.
     *
     * [Insertion.index] is where the first added page actually landed, so a caller that reports or
     * scrolls to it can't drift out of step with this function's own clamping.
     */
    fun insertAfter(pages: List<Page>, after: Int, added: List<Page>): Insertion {
        if (added.isEmpty()) return Insertion(pages, (after + 1).coerceIn(0, pages.size))
        val at = (after + 1).coerceIn(0, pages.size)
        return Insertion(pages.toMutableList().apply { addAll(at, added) }, at)
    }

    /** The result of [insertAfter]: the new page list and the index the first added page landed at. */
    data class Insertion(val pages: List<Page>, val index: Int)

    /** A copy of [pages] with page [index] removed. The last remaining page is never removed. */
    fun removeAt(pages: List<Page>, index: Int): List<Page> {
        if (pages.size <= 1) return pages
        val i = index.coerceIn(0, pages.lastIndex)
        return withoutAt(pages, i)
    }

    /**
     * [pages] with one page inserted at [at], as a **view** rather than a copy.
     *
     * A page list can be a lazy view of a `.mobix`'s archive (`LazyPages`), where `toMutableList`
     * would inflate every page of a two-hundred-page notebook to add one sheet. The wrapper answers
     * index `at` with the new page and shifts the rest by one, reading through to the original list —
     * so a save still finds the untouched pages as the archive's own.
     */
    private fun insertedAt(pages: List<Page>, at: Int, page: Page): List<Page> = object : AbstractList<Page>() {
        override val size: Int get() = pages.size + 1

        override fun get(index: Int): Page = when {
            index < at -> pages[index]
            index == at -> page
            else -> pages[index - 1]
        }
    }

    /** [pages] without index [at], as the same kind of view — the deletion half of [insertedAt]. */
    private fun withoutAt(pages: List<Page>, at: Int): List<Page> = object : AbstractList<Page>() {
        override val size: Int get() = pages.size - 1

        override fun get(index: Int): Page = if (index < at) pages[index] else pages[index + 1]
    }

    /**
     * A copy of [pages] with every page in [indices] removed — the page-overview multi-select delete.
     * Out-of-range indices are ignored. A document is never emptied: a selection covering *every* page
     * is refused outright (the list is returned unchanged) rather than silently sparing an arbitrary
     * page, and an empty or fully out-of-range selection is likewise a no-op returning the same
     * instance, so callers can hand the result straight to an "only if it differs" edit.
     */
    fun removeAll(pages: List<Page>, indices: Set<Int>): List<Page> {
        val drop = indices.filter { it in pages.indices }.toSet()
        if (drop.isEmpty() || drop.size >= pages.size) return pages
        return pages.filterIndexed { i, _ -> i !in drop }
    }
}
