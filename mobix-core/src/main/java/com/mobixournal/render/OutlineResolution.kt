package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page

/**
 * One **Contents row** as a reader sees it: a title, how deep it sits, and the **document page** it opens —
 * null for a row that opens nothing.
 *
 * [page] is a document page index, not a PDF sheet number: the two agree only by construction, and the whole
 * point of resolving here is that they do not have to (`docs/pdf.md`). [id] is the row's identity — a
 * `bkm_` node id from a notebook tree, or a synthetic `pdf:<position>` for a row read out of a PDF that has
 * no notebook tree yet — so a row can be renamed or moved and still be *the same row* to a selection, a
 * filter and an undo.
 *
 * The remaining fields are what a **view** fills in for the rows it decides to draw ([PdfOutlineView]):
 * [row] the position in the resolved list, [branch] whether it owns rows under it, [expanded] whether they
 * are on screen, [context] that it is drawn only to keep a match reachable, [matched] that its own title
 * carries the query, and [current] that it names the page the reader is standing on.
 */
data class OutlineRow(
    val id: String,
    val title: String,
    val depth: Int,
    val page: Int?,
    val row: Int = 0,
    val branch: Boolean = false,
    val expanded: Boolean = true,
    val context: Boolean = false,
    val matched: Boolean = false,
    val current: Boolean = false,
) {

    /** True when this row names a page a tap can open. */
    val hasDestination: Boolean get() = page != null
}

/**
 * **Where a Contents row leads**, asked once for both shells — the notebook's own tree first, the PDF's
 * outline as the fallback, and never a guess when neither answers.
 *
 * The two kinds of target are resolved differently on purpose:
 *
 *  - a `sheet` target names a page of the document's PDF, so it is found by asking which page's background
 *    shows that sheet — the same inheritance the painter and the exporter use — and the **first** such page
 *    wins, because the rows are in document order;
 *  - a `page` target names a notebook page by its `.mobix` id, so it is found by identity and survives a
 *    reorder, and a duplicated page (a new id) never inherits the bookmark.
 *
 * A target that finds nothing — a deleted page, a PDF that is no longer there, a `.xopp` whose pages carry
 * no ids — yields a row with no destination. That row is still drawn: it is the reader's own bookmark, and
 * the honest answer to "where does this go" is that it no longer goes anywhere. Nothing here rounds a
 * missing page onto its neighbour, which is what would make a stale bookmark open the wrong chapter.
 */
object OutlineResolution {

    /** The first page in document order showing PDF page [sheet], or null when none does. */
    fun pageOfSheet(pages: List<Page>, sheet: Int): Int? {
        val index = pages.indexOfFirst { (it.background as? Background.Pdf)?.pageNo == sheet }
        return index.takeIf { it >= 0 }
    }

    /**
     * The notebook's tree as rows, in pre-order, each carrying the position the view addresses it by.
     *
     * A node's title is shown as it is; an empty title is left empty for the caller to label, since only the
     * shell knows what to call a row the reader has not named yet.
     */
    fun rows(tree: OutlineTree, pages: List<Page>, checkActive: () -> Unit = {}): List<OutlineRow> {
        val targets = HashSet<OutlineTarget>()
        fun targetsOf(nodes: List<OutlineNode>) {
            for (node in nodes) { node.target?.let(targets::add); targetsOf(node.children) }
        }
        targetsOf(tree.nodes)
        val index = targetIndex(pages, targets, checkActive)
        val out = ArrayList<OutlineRow>(tree.size)
        fun walk(nodes: List<OutlineNode>, depth: Int) {
            for (node in nodes) {
                out += OutlineRow(
                    id = node.id,
                    title = node.title,
                    depth = depth,
                    page = node.target?.let { index[it] },
                    row = out.size,
                )
                walk(node.children, depth + 1)
            }
        }
        walk(tree.nodes, 0)
        return out
    }

    /**
     * A PDF's own outline as rows — what a document that has no notebook tree yet shows, and what an import
     * seeds a tree from. The ids are synthetic and positional (`pdf:0`, `pdf:1`, …): there is nothing more
     * durable to call a row of a file this app does not own.
     */
    fun rows(outline: PdfOutline, pages: List<Page>, checkActive: () -> Unit = {}): List<OutlineRow> {
        val targets = outline.entries.filter { it.hasDestination }.mapTo(HashSet<OutlineTarget>()) { OutlineTarget.Sheet(it.pageIndex) }
        val lookup = targetIndex(pages, targets, checkActive)
        return outline.entries.mapIndexed { index, entry ->
            OutlineRow("pdf:$index", entry.title, entry.depth,
                if (entry.hasDestination) lookup[OutlineTarget.Sheet(entry.pageIndex)] else null, row = index)
        }
    }

    /** Each page is read at most once; identity-only trees use manifest ids without opening page models. */
    private fun targetIndex(pages: List<Page>, targets: Set<OutlineTarget>, checkActive: () -> Unit): Map<OutlineTarget, Int> {
        if (targets.isEmpty()) return emptyMap()
        val source = PageSource.of(pages)
        val needsSheets = targets.any { it is OutlineTarget.Sheet }
        val found = HashMap<OutlineTarget, Int>()
        for (at in 0 until source.pageCount) {
            checkActive()
            val page = if (needsSheets) source.page(at) else null
            val id = if (needsSheets) page?.id else source.pageId(at)
            id?.let { OutlineTarget.Page(it) }?.takeIf { it in targets }?.let { found.putIfAbsent(it, at) }
            (page?.background as? Background.Pdf)?.let { OutlineTarget.Sheet(it.pageNo) }
                ?.takeIf { it in targets }?.let { found.putIfAbsent(it, at) }
            if (found.size == targets.size) break
        }
        return found
    }

    /**
     * The rows a document's Contents should show: its own tree when it has one, and the PDF's outline
     * otherwise — the fallback that makes the feature work on a document nobody has edited the index of yet.
     */
    fun rowsOf(tree: OutlineTree, outline: PdfOutline?, pages: List<Page>, checkActive: () -> Unit = {}): List<OutlineRow> = when {
        !tree.isEmpty -> rows(tree, pages, checkActive)
        outline != null -> rows(outline, pages, checkActive)
        else -> emptyList()
    }

    /** Where one target leads in [pages], or null when it leads nowhere any more. */
    fun pageOf(pages: List<Page>, target: OutlineTarget): Int? = when (target) {
        is OutlineTarget.Sheet -> pageOfSheet(pages, target.pageNo)
        is OutlineTarget.Page -> pages.indexOfFirst { it.id == target.pageId }.takeIf { it >= 0 }
    }
}
