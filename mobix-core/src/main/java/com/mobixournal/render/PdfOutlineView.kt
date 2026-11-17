package com.mobixournal.render

/**
 * **What a reader is looking at in the Contents**: which rows a branch's expansion lets through, and which of
 * them a title search keeps — the same answer for the tablet's card and the window's sidebar.
 *
 * The rows are already flat and already resolved ([OutlineResolution]); what this owns is the *view* of them,
 * and the one rule that made a filter and an expansion fight each other. A bookmark's **title is not its
 * identity**: a book can carry two chapters called "Introduction", and a `Set<String>` of collapsed titles
 * would collapse both at once. So a branch is addressed by its **row id** ([OutlineRow.id]) — a `bkm_` node id,
 * or the `pdf:<position>` id a source PDF's rows get — which survives an insert above it and an undo, where a
 * position in the resolved list does not.
 *
 * The two rules, in one place:
 * - **Expansion** hides a row when any of its ancestors is collapsed. A branch is a row whose next row is
 *   deeper than it — the tree's own nesting, not a title's.
 * - **A filter** overrides expansion for as long as it is on (a reader searching for a section wants to see
 *   it, not to hunt for the branch it hides under), keeps the **ancestors** of every match as context, and
 *   never touches the collapse state — so clearing the box puts the tree back exactly as it was.
 *
 * Pure and JVM-tested: no Compose, no PDFBox, no file.
 */
object PdfOutlineView {

    /** The rows that have children in [rows] — the rows that draw an expansion control. */
    fun branchRows(rows: List<OutlineRow>): Set<Int> {
        val branches = HashSet<Int>()
        for (row in rows.indices) {
            if (row < rows.lastIndex && rows[row + 1].depth > rows[row].depth) branches += row
        }
        return branches
    }

    /** The first row after [row] that is not under it — the end (exclusive) of its branch. */
    fun endOfBranch(rows: List<OutlineRow>, row: Int): Int {
        if (row !in rows.indices) return rows.size
        val depth = rows[row].depth
        var next = row + 1
        while (next < rows.size && rows[next].depth > depth) next++
        return next
    }

    /**
     * [collapsed] with the branch of the row whose **id** is [id] flipped — the one state a chevron changes.
     *
     * A branch is addressed by its node id, never by its position: inserting a bookmark above a closed chapter,
     * renaming one, or undoing an edit shifts every position after it, and a position-keyed set would then close
     * a different chapter. A row read out of the PDF has no node id of its own; its `pdf:<position>` id is
     * deterministic for that source, which is the fallback the resolvers write.
     */
    fun toggled(collapsed: Set<String>, id: String): Set<String> =
        if (id in collapsed) collapsed - id else collapsed + id

    /** [collapsed] cut down to the rows that still exist, so a removed branch cannot shadow a later one. */
    fun pruned(collapsed: Set<String>, rows: List<OutlineRow>): Set<String> {
        if (collapsed.isEmpty()) return collapsed
        val live = rows.mapTo(HashSet(rows.size)) { it.id }
        return collapsed.intersect(live)
    }

    /**
     * The rows to draw, in order, for a [collapsed] set, a [query] and the page the reader stands on.
     *
     * Every returned row carries its flags set: [OutlineRow.branch] for a row that owns others,
     * [OutlineRow.expanded] for whether they are showing, [OutlineRow.context] for an ancestor kept only to
     * make a match reachable, [OutlineRow.matched] for the match itself and [OutlineRow.current] for the row
     * naming [currentPage] (the **last** such row wins, since a section and its chapter often share a page).
     * With a query and no hit the list is empty, which the caller says out loud rather than showing an empty
     * card.
     */
    fun visible(
        rows: List<OutlineRow>,
        collapsed: Set<String> = emptySet(),
        query: String = "",
        currentPage: Int? = null,
    ): List<OutlineRow> {
        val branches = branchRows(rows)
        val current = currentRow(rows, currentPage)
        val needle = query.trim()
        val matched = if (needle.isEmpty()) {
            null
        } else {
            val hits = rows.indices.filter { rows[it].title.contains(needle, ignoreCase = true) }
            if (hits.isEmpty()) return emptyList()
            matchingContext(rows, hits.toSet())
        }
        val drawn = matched?.sorted() ?: withinBranches(rows, collapsed)
        val hits = rows.indices.filter { matched != null && rows[it].title.contains(needle, ignoreCase = true) }.toSet()
        return drawn.map { index ->
            rows[index].copy(
                row = index,
                branch = index in branches,
                expanded = rows[index].id !in collapsed,
                context = matched != null && index !in hits,
                matched = index in hits,
                current = index == current,
            )
        }
    }

    /**
     * The row whose destination is [page] — the page the reader is standing on — or null when no row names
     * it. The **last** such row wins: a later bookmark pointing at the same page is the finer-grained one.
     */
    fun currentRow(rows: List<OutlineRow>, page: Int?): Int? {
        if (page == null || page < 0) return null
        return rows.indexOfLast { it.page == page }.takeIf { it >= 0 }
    }

    /** True when [row] carries the filter's own words, so the caller can mark the match itself. */
    fun isMatch(row: OutlineRow, query: String): Boolean {
        val needle = query.trim()
        return needle.isNotEmpty() && row.title.contains(needle, ignoreCase = true)
    }

    /** The indices the tree shows with its branches closed: what a reader sees before searching. */
    private fun withinBranches(rows: List<OutlineRow>, collapsed: Set<String>): List<Int> {
        if (collapsed.isEmpty()) return rows.indices.toList()
        val drawn = ArrayList<Int>(rows.size)
        // One cursor: everything up to [skipUntil] is inside a closed branch and is not drawn.
        var skipUntil = 0
        rows.forEachIndexed { row, entry ->
            if (row < skipUntil) return@forEachIndexed
            drawn += row
            if (entry.id in collapsed) skipUntil = endOfBranch(rows, row)
        }
        return drawn
    }

    /** One forward walk keeps the ancestor path, including filters matching thousands of children. */
    private fun matchingContext(rows: List<OutlineRow>, hits: Set<Int>): Set<Int> {
        val kept = HashSet<Int>()
        val ancestors = ArrayList<Int>()
        for (index in rows.indices) {
            while (ancestors.isNotEmpty() && rows[ancestors.last()].depth >= rows[index].depth) {
                ancestors.removeAt(ancestors.lastIndex)
            }
            if (index in hits) {
                kept += index
                kept += ancestors
            }
            ancestors += index
        }
        return kept
    }
}
