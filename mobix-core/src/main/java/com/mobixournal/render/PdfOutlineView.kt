package com.mobixournal.render

/**
 * **What a reader is looking at in a PDF's Contents**: which rows a branch's expansion lets through, and
 * which of them a title search keeps — the same answer for the tablet's card and the window's sidebar.
 *
 * The tree is already flat ([PdfOutline]); what was missing is the *view* of it, and the one rule that made
 * a filter and an expansion fight each other. A bookmark's **title is not its identity**: a book can carry
 * two chapters called "Introduction", and a `Set<String>` of collapsed titles would collapse both at once.
 * So every row is addressed by its **position** in the flattened outline ([PdfOutlineEntry] index), which is
 * stable for the life of the file, and a branch is that position plus the span of rows under it.
 *
 * The two rules, in one place:
 * - **Expansion** hides a row when any of its ancestors is collapsed. A branch is a row whose next row is
 *   deeper than it — the file's own nesting, not a title's.
 * - **A filter** overrides expansion for as long as it is on (a reader searching for a section wants to see
 *   it, not to hunt for the branch it hides under), keeps the **ancestors** of every match as context, and
 *   never touches the collapse state — so clearing the box puts the tree back exactly as it was.
 *
 * Pure and JVM-tested: no Compose, no PDFBox, no file.
 */
object PdfOutlineView {

    /**
     * The rows that have children in [entries] — the rows that draw an expansion control.
     *
     * A row owns everything after it until the first row that is **not deeper**: that is what pre-order
     * flattening means, and it is why this needs no extra field on [PdfOutlineEntry].
     */
    fun branchRows(entries: List<PdfOutlineEntry>): Set<Int> {
        val branches = HashSet<Int>()
        for (row in entries.indices) {
            if (endOfBranch(entries, row) > row + 1) branches += row
        }
        return branches
    }

    /** The first row after [row] that is not under it — the end (exclusive) of its branch. */
    fun endOfBranch(entries: List<PdfOutlineEntry>, row: Int): Int {
        if (row !in entries.indices) return entries.size
        val depth = entries[row].depth
        var next = row + 1
        while (next < entries.size && entries[next].depth > depth) next++
        return next
    }

    /** [collapsed] with [row]'s branch flipped: the one state a chevron changes. */
    fun toggled(collapsed: Set<Int>, row: Int): Set<Int> =
        if (row in collapsed) collapsed - row else collapsed + row

    /**
     * The rows to draw, in outline order, for a [collapsed] set and a [query].
     *
     * With no query this is the collapsed tree. With one, every row whose title contains it (case ignored)
     * is shown, together with the ancestors that lead to it, flags included so the caller can dim the
     * context. An empty result means the file has no bookmark with that word, which the caller says out
     * loud rather than showing an empty card.
     */
    fun visible(
        entries: List<PdfOutlineEntry>,
        collapsed: Set<Int> = emptySet(),
        query: String = "",
    ): List<PdfOutlineVisible> {
        val needle = query.trim()
        if (needle.isEmpty()) return withinBranches(entries, collapsed)
        val matched = entries.indices.filter { entries[it].title.contains(needle, ignoreCase = true) }
        if (matched.isEmpty()) return emptyList()
        val kept = HashSet<Int>()
        for (row in matched) {
            kept += row
            kept += ancestorsOf(entries, row)
        }
        return entries.indices.filter { it in kept }.map { row ->
            PdfOutlineVisible(
                row = row,
                entry = entries[row],
                context = row !in matched,
                matched = row in matched,
            )
        }
    }

    /**
     * The row whose destination is [pdfPage] — the one the reader is standing on — or null when no bookmark
     * names that sheet. The **last** such row wins, since a later bookmark pointing at the same page is the
     * finer-grained one (a chapter and its first section often share a page).
     */
    fun currentRow(entries: List<PdfOutlineEntry>, pdfPage: Int?): Int? {
        if (pdfPage == null || pdfPage < 0) return null
        return entries.indexOfLast { it.hasDestination && it.pageIndex == pdfPage }.takeIf { it >= 0 }
    }

    /** True when [row] carries the filter's own words, so the caller can mark the match itself. */
    fun isMatch(entry: PdfOutlineEntry, query: String): Boolean {
        val needle = query.trim()
        return needle.isNotEmpty() && entry.title.contains(needle, ignoreCase = true)
    }

    /** The tree with its collapsed branches closed: what a reader sees before searching. */
    private fun withinBranches(
        entries: List<PdfOutlineEntry>,
        collapsed: Set<Int>,
    ): List<PdfOutlineVisible> {
        if (collapsed.isEmpty()) return entries.mapIndexed { row, entry -> visible(row, entry) }
        val rows = ArrayList<PdfOutlineVisible>(entries.size)
        // One cursor: everything up to [skipUntil] is inside a closed branch and is not drawn.
        var skipUntil = 0
        entries.forEachIndexed { row, entry ->
            if (row < skipUntil) return@forEachIndexed
            rows += visible(row, entry)
            if (row in collapsed) skipUntil = endOfBranch(entries, row)
        }
        return rows
    }

    private fun visible(row: Int, entry: PdfOutlineEntry) = PdfOutlineVisible(row, entry, context = false, matched = false)

    /** The rows above [row] that lead to it: the nearest shallower row, then its own, and so on. */
    private fun ancestorsOf(entries: List<PdfOutlineEntry>, row: Int): List<Int> {
        val path = ArrayList<Int>()
        var wanted = entries[row].depth
        var at = row - 1
        while (at >= 0 && wanted > 0) {
            val entry = entries[at]
            if (entry.depth < wanted) {
                path += at
                wanted = entry.depth
            }
            at--
        }
        return path
    }
}

/**
 * One row a reader is shown, addressed by its position in the outline's flat list.
 *
 * [context] says the row is drawn only to keep its matching descendant reachable (an ancestor path), and
 * [matched] that its own title carries the search's words — so a list can mark the hits and leave the path
 * to them quiet instead of shouting every row.
 */
data class PdfOutlineVisible(
    val row: Int,
    val entry: PdfOutlineEntry,
    val context: Boolean,
    val matched: Boolean,
)
