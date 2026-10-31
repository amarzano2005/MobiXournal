package com.mobixournal.render

/**
 * A PDF's **outline** — the bookmark/"Contents" tree a desktop reader shows in its sidebar —
 * flattened into display rows.
 *
 * The tree itself is read from the file by [PdfOutlineExtractor] (PDFBox glue, since it is already a
 * dependency for text and export); the shape below is Android-free and JVM-tested. An entry names a
 * title and the **PDF** page it jumps to — mapping that onto the document page showing it is the
 * caller's job (`DrawingSurfaceView.goToPdfPage`), because the `.xopp` page order and the PDF's are
 * only the same by construction, not by rule.
 */

/** One display row of a PDF outline: a title, where it points, and how deep it nests. */
data class PdfOutlineEntry(
    /** The entry's text as the PDF stores it, whitespace collapsed. */
    val title: String,
    /** 0-based **PDF** page index this entry jumps to, or [NO_DESTINATION] when it points nowhere. */
    val pageIndex: Int,
    /** Nesting level: 0 for a top-level chapter, 1 for a section under it, and so on. */
    val depth: Int,
) {
    /** True when this row names a page the reader can jump to. */
    val hasDestination: Boolean get() = pageIndex >= 0

    companion object {
        /** [pageIndex] for an entry with no resolvable destination (a header that only groups rows). */
        const val NO_DESTINATION = -1
    }
}

/**
 * A PDF outline in reading order (depth-first pre-order), ready for a list.
 *
 * A row whose own entry carries no destination inherits the page of the nearest descendant that
 * does, so a chapter header still leads to the chapter's first page; when nothing below it points
 * anywhere either, the row is shown but not jumpable.
 */
class PdfOutline(val entries: List<PdfOutlineEntry>) {

    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {
        val EMPTY = PdfOutline(emptyList())
    }
}

/** One node of the raw outline tree, before it is flattened and its holes filled in. */
data class PdfOutlineNode(
    val title: String,
    val pageIndex: Int,
    val children: List<PdfOutlineNode>,
)

/** Flattening of the raw tree: pre-order walk, whitespace collapsed, blanks dropped, depth capped. */
object PdfOutlineTree {

    /** Rows kept from one outline; a corrupt or hostile file can't turn into an unbounded list. */
    const val MAX_ENTRIES = 1000

    /** Nesting levels kept as-is; deeper rows are clamped so the indent never runs off-screen. */
    const val MAX_DEPTH = 8

    private val WHITESPACE = Regex("\\s+")

    fun flatten(roots: List<PdfOutlineNode>): PdfOutline {
        if (roots.isEmpty()) return PdfOutline.EMPTY
        val rows = ArrayList<PdfOutlineEntry>()
        walk(roots, 0, PdfOutlineEntry.NO_DESTINATION, rows)
        return PdfOutline(rows)
    }

    /**
     * Emit [nodes] then descend into each one's children. [inherited] is the page the nearest
     * ancestor settled on, so a destination-less header can borrow it when none of its own
     * descendants names a page either.
     */
    private fun walk(
        nodes: List<PdfOutlineNode>,
        depth: Int,
        inherited: Int,
        rows: MutableList<PdfOutlineEntry>,
    ) {
        for (node in nodes) {
            if (rows.size >= MAX_ENTRIES) return
            val title = node.title.replace(WHITESPACE, " ").trim()
            val page = if (node.pageIndex >= 0) node.pageIndex else node.descendantPage(inherited)
            if (title.isNotEmpty()) {
                rows += PdfOutlineEntry(title, page, depth.coerceAtMost(MAX_DEPTH))
            }
            // A titleless node is a pure container: it adds no row of its own, and its children stay
            // at the same level rather than being pushed a rung deeper by an invisible wrapper.
            walk(
                nodes = node.children,
                depth = if (title.isEmpty()) depth else depth + 1,
                inherited = if (page >= 0) page else inherited,
                rows = rows,
            )
        }
    }

    /** The page of the first node at or below this one that names one, else [inherited]. */
    private fun PdfOutlineNode.descendantPage(inherited: Int): Int {
        if (pageIndex >= 0) return pageIndex
        for (child in children) {
            val page = child.descendantPage(PdfOutlineEntry.NO_DESTINATION)
            if (page >= 0) return page
        }
        return inherited
    }
}
