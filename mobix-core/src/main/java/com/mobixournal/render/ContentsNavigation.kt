package com.mobixournal.render

/**
 * **What a reader has done to a Contents view and where they left it** — the filter words, the branches closed
 * and the row at the top of the list — as one value, so both shells keep it in a single state object and a test
 * can replay the transitions without Compose.
 *
 * The bug this answers is a list that forgets: a query and a fold were remembered **against the rows**, so the
 * moment an edit replaced the row list the filter reset, every branch opened and the reader was thrown back to
 * the top of a thousand bookmarks. Nothing about reading is the document's, and nothing about it should change
 * because a neighbouring bookmark was renamed.
 *
 * Two rules are the whole of it:
 * - the fold and the anchor are addressed by row **id** ([OutlineRow.id]), never by position, so an insert
 *   above a closed chapter does not close another one;
 * - when the anchored row itself is gone (it was the one deleted, or it no longer matches the filter), the
 *   nearest surviving row takes its place — the list moves by one line, not to the top.
 */
data class ContentsNavigation(
    val query: String = "",
    val collapsed: Set<String> = emptySet(),
    /** The id of the row at the top of the list, or null while nothing has been scrolled yet. */
    val anchorId: String? = null,
    /** That row's position in the resolved rows, kept so a vanished anchor has somewhere to fall back to. */
    val anchorIndex: Int = 0,
) {

    /** The filter's words: folding is not touched, so clearing the query gives the tree back as it was left. */
    fun withQuery(value: String): ContentsNavigation = copy(query = value)

    /** A chevron: one branch of the row whose id is [id]. */
    fun toggled(id: String): ContentsNavigation =
        copy(collapsed = if (id in collapsed) collapsed - id else collapsed + id)

    /** Where the list is now: the id it shows first and that row's place among the resolved rows. */
    fun scrolledTo(id: String?, index: Int): ContentsNavigation = copy(anchorId = id, anchorIndex = index)

    /**
     * This navigation against a new row list: folds of rows that no longer exist are dropped, and an anchor
     * that has gone is replaced by the surviving row nearest where it stood.
     */
    fun against(rows: List<OutlineRow>): ContentsNavigation {
        val folds = PdfOutlineView.pruned(collapsed, rows)
        if (rows.isEmpty()) return copy(collapsed = folds, anchorId = null, anchorIndex = 0)
        // A list nobody has scrolled has no anchor to keep: `null` stays `null` rather than becoming row zero.
        if (anchorId == null) return copy(collapsed = folds)
        val at = rows.indexOfFirst { it.id == anchorId }
        if (at >= 0) return copy(collapsed = folds, anchorIndex = at)
        val nearest = anchorIndex.coerceIn(0, rows.lastIndex)
        return copy(collapsed = folds, anchorId = rows[nearest].id, anchorIndex = nearest)
    }
}
