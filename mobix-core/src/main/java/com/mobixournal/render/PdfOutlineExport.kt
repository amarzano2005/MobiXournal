package com.mobixournal.render

import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page

/**
 * **The notebook's Contents, as a PDF outline for an export.**
 *
 * The rows a reader renamed, moved and deleted are the document's own index, so an exported PDF carries
 * them in its bookmark pane; the rows an import brought in keep their places under the file they came from
 * ([PdfOutlineSeeding]). Nothing here is PDFBox: this object decides *what* an exported row says and *where*
 * in the written file it leads, and each shell writes the answer with the PDFBox it has.
 *
 * **Pages are the export's, not the file's.** A destination is resolved to a notebook page
 * ([OutlineResolution.pageOf]) and then to the page's position **among the exported pages** — an export of
 * pages 3, 7 and 8 writes them as 0, 1 and 2, and a bookmark that opened notebook page 7 opens the second
 * sheet of the file. Reordering the notebook therefore reorders the outline with it.
 *
 * **A page that is not exported keeps its nesting, never its destination.** The rule, in one sentence: a row
 * whose page is missing from the export is written as a **heading with no destination** when something
 * still exported hangs under it, and is dropped when nothing does — so a chapter whose own page was left out
 * still groups its exported sections, and a bookmark to a page nobody picked does not become a link to
 * nothing. A target that leads nowhere in the document at all (a deleted page) is treated exactly the same
 * way, which is why the two cases share one rule instead of two.
 *
 * Titles are written as the reader typed them, duplicates included: two chapters called *Introduction* are
 * two rows, and the file's outline is not the place to rename them.
 */
object PdfOutlineExport {

    /**
     * One written outline row: its [title], how [depth] levels deep it sits (0 at the root) and the
     * **exported page** it opens, or null for a heading that only groups what is under it.
     */
    data class Row(val title: String, val depth: Int, val page: Int?)

    /**
     * The rows [tree] writes for an export of [exportedPages] (notebook indices, in the order written).
     *
     * [pages] is the document the tree belongs to: it is what a target is resolved against, so a row naming
     * a page the document no longer has drops its destination rather than pointing at a neighbour.
     */
    fun rows(tree: OutlineTree, pages: List<Page>, exportedPages: List<Int>): List<Row> {
        val position = HashMap<Int, Int>()
        exportedPages.forEachIndexed { written, page -> position[page] = written }
        return kept(tree.nodes, pages, position, depth = 0)
    }

    /** The rows of [nodes] that survive, pruned by the rule in this object's own KDoc. */
    private fun kept(
        nodes: List<OutlineNode>,
        pages: List<Page>,
        position: Map<Int, Int>,
        depth: Int,
    ): List<Row> {
        val rows = ArrayList<Row>()
        for (node in nodes) {
            val under = kept(node.children, pages, position, depth + 1)
            val page = node.target?.let { OutlineResolution.pageOf(pages, it) }?.let { position[it] }
            if (page == null && under.isEmpty()) continue
            rows += Row(title = titleOf(node, page), depth = depth, page = page)
            rows += under
        }
        return rows
    }

    /**
     * The row's name: the reader's own, or — for a row that was never named — the page it opens. A PDF
     * outline item with an empty title is drawn as a blank line a reader cannot click with confidence.
     */
    private fun titleOf(node: OutlineNode, page: Int?): String = when {
        node.title.isNotBlank() -> node.title
        page != null -> "Page ${page + 1}"
        else -> "Section"
    }
}
