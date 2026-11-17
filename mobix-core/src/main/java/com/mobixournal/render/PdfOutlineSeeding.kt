package com.mobixournal.render

import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree

/**
 * **Filling a notebook's Contents from the PDF an import brings.**
 *
 * The PDF's own bookmark tree is read once, at import, by [PdfOutlineExtractor] (PDFBox glue, each shell's
 * own copy). From then on the rows belong to the document: the reader renames, moves and deletes them, so
 * this is a **seed**, never a mirror — a document that already carries an outline is never re-seeded, and an
 * entry the reader deleted stays deleted ([OutlineTree] is the notebook's own index, `docs/pdf.md`).
 *
 * Original chapter roots append directly in reading order and nesting. An explicit filename marker can
 * group them under one destination-bearing row; both shells leave that option off by default. Existing
 * authored trees are never flattened by guessing whether a title once came from a filename.
 *
 * Three things this rule pins, each of them a way a seed can look right and behave wrong:
 * 1. **Every row mints its own `bkm_` id** ([ElementId.BOOKMARK]). Ids are the row's identity — the thing a
 *    rename, a drag and an undo compare — so they can never be derived from a title or a position.
 * 2. **A destination is written explicitly** as [OutlineTarget.Sheet], for every row that has one. Reading
 *    inherits nothing (`OutlineResolution` answers exactly what the row names), so a chapter header the PDF
 *    left destination-less must be written with the page it **inherited from its first pointing descendant**
 *    — [PdfOutlineTree.flatten] already resolves that, and this object never drops it.
 * 3. **A joined import's pages are shifted** by [pageOffset], because a merge renumbers the arriving sheets
 *    inside the one PDF file the document carries; a row naming the picked file's page 3 must name the
 *    joined file's page 3 + the base's count.
 *
 * Pure: no PDFBox, no document, no clock. The shell hands in the entries and commits the tree with the pages
 * as one edit.
 */
object PdfOutlineSeeding {

    /**
     * The tree [outline] seeds, or [OutlineTree.EMPTY] for a PDF with no bookmarks.
     *
     * [pageOffset] is added to every destination — 0 for an import into a paper notebook or a replacing
     * import, the base PDF's page count for a joined append. [mint] exists for tests; production keeps the
     * document's own id generator.
     */
    fun tree(
        outline: PdfOutline,
        pageOffset: Int = 0,
        mint: (String) -> String = { ElementId.mint(ElementId.BOOKMARK) },
    ): OutlineTree {
        if (outline.isEmpty) return OutlineTree.EMPTY
        val rows = Seeder(offset = pageOffset, mint = mint)
            .siblings(outline.entries, start = 0, depth = 0)
            .first
        return if (rows.isEmpty()) OutlineTree.EMPTY else OutlineTree(nodes = rows)
    }

    /**
     * [existing] plus **one row named after the imported file**, pointing at [firstPage], with [chapters]
     * under it — an optional marker the reader explicitly requests.
     *
     * The row is the file's own signpost: it says which book these pages came from and opens the first page
     * that actually arrived (a subset's first pick, not the file's page 1). The chapters keep their hierarchy
     * under it, so a book with bookmarks reads as one object and a book without them is still one row instead
     * of nothing. A null or blank [title] is the reader turning the option off: those rows then join [existing]
     * at the top level, exactly as an import used to.
     */
    fun imported(
        existing: OutlineTree,
        chapters: OutlineTree?,
        title: String?,
        firstPage: Int,
        mint: (String) -> String = { ElementId.mint(ElementId.BOOKMARK) },
    ): OutlineTree {
        val rows = chapters?.nodes.orEmpty()
        if (title.isNullOrBlank()) return existing.copy(nodes = existing.nodes + rows)
        val marker = OutlineNode(
            id = mint(ElementId.BOOKMARK),
            title = title.trim(),
            target = OutlineTarget.Sheet(firstPage),
            children = rows,
        )
        return OutlineTree(nodes = existing.nodes + marker)
    }

    /**
     * The bookmark title an imported file offers: its own name with **only** the final `.pdf` dropped, in any
     * case, and nothing else rewritten — `notes.final.PDF` is *notes.final*, and a file called `.pdf` keeps it.
     */
    fun defaultTitle(fileName: String): String {
        val name = fileName.trim()
        return if (name.length > 4 && name.endsWith(".pdf", ignoreCase = true)) name.dropLast(4) else name
    }

    /** One pre-order walk of the flattened rows, nesting by the depth each entry carries. */
    private class Seeder(private val offset: Int, private val mint: (String) -> String) {

        /**
         * The rows at [depth] starting at [start], and where the walk stopped.
         *
         * Rows deeper than [depth] belong to the row just before them and are consumed by the recursive call;
         * a malformed jump (a level that skipped its parent) keeps its own row at the level it claims.
         */
        fun siblings(entries: List<PdfOutlineEntry>, start: Int, depth: Int): Pair<List<OutlineNode>, Int> {
            val nodes = ArrayList<OutlineNode>()
            var index = start
            while (index < entries.size && entries[index].depth >= depth) {
                val entry = entries[index]
                index++
                val (children, next) = siblings(entries, index, depth + 1)
                nodes += OutlineNode(
                    id = mint(ElementId.BOOKMARK),
                    title = entry.title,
                    // Explicit, always: reading inherits nothing, so the page the PDF's own tree resolved
                    // (a header's first pointing descendant included) is written down here.
                    target = entry.pageIndex.takeIf { it >= 0 }?.let { OutlineTarget.Sheet(it + offset) },
                    children = children,
                )
                index = next
            }
            return nodes to index
        }
    }
}
