package com.mobixournal.render

/**
 * **Which notebook pages an export writes** — one rule for the tablet's card and the window's.
 *
 * An export used to be the whole document, always. A reader who wanted a chapter of a notebook had to
 * export everything and cut it apart, so both shells now ask the same three-way question: every page, the
 * page in view, or a set the reader names. The answer is a [PageRangePlan], which means the typed case
 * speaks the grammar the import card already teaches (`2,4-6,9`, [PageRanges.selection]) and a shell cannot
 * disagree with the other about what a number means.
 *
 * Indices are the **notebook's**, 0-based, in the order the document holds them — not the source PDF's page
 * numbers. A reordered notebook exports its pages in the order they read on screen, which is what the file
 * the reader opens should show.
 */
enum class ExportPageScope {
    /** Every page of the document — the default, and what an export always was. */
    ALL,

    /** Only the page in view when the card was opened. */
    CURRENT,

    /** The pages the field names, in the document's own order. */
    SELECTED,
}

object ExportPages {

    /**
     * The pages [scope] names for a document of [pageCount] pages whose page in view is [current], or the
     * sentence saying why it names none ([PageRangePlan.Invalid]).
     *
     * [current] is clamped rather than refused: a tab that lost its page to a delete while the card was up
     * should still export something, and the page nearest the one it meant is that page.
     */
    fun resolve(scope: ExportPageScope, current: Int, text: String, pageCount: Int): PageRangePlan {
        if (pageCount <= 0) return PageRangePlan.Invalid("There are no pages to work on.")
        return when (scope) {
            ExportPageScope.ALL -> PageRangePlan.Chosen((0 until pageCount).toList())
            ExportPageScope.CURRENT -> PageRangePlan.Chosen(listOf(current.coerceIn(0, pageCount - 1)))
            ExportPageScope.SELECTED -> PageRanges.selection(text, pageCount)
        }
    }

    /**
     * Whether **Export PDF** has anything worth asking: a document of one page has one answer, so both shells
     * skip the card and hand the file straight to the destination picker. A document with **no** page still
     * asks, because the card is where the reader is told there is nothing to work on.
     */
    fun needsPageChoice(pageCount: Int): Boolean = pageCount != 1

    /** `1 page` / `3 pages` — a card that says "1 pages" reads as a machine talking. */
    fun count(count: Int): String = if (count == 1) "1 page" else "$count pages"

    /**
     * The line beside the field: the count the answer comes to, out of how many the document has — or, while
     * the field names nothing, the shape of an answer. The refusal itself is the plan's own sentence.
     */
    fun readout(plan: PageRangePlan, pageCount: Int): String {
        val indices = plan.indices()
            ?: return (plan as PageRangePlan.Invalid).message
        if (indices.isEmpty()) return "Type pages or runs, like 2,4-6,9"
        return "${count(indices.size)} of $pageCount"
    }
}
