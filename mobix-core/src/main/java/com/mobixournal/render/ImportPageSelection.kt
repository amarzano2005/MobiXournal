package com.mobixournal.render

/**
 * **What the import thumbnails do to a page selection**, as pure rules both import cards share.
 *
 * The grid and the typed field are two views of one answer: tapping page 4 rewrites the field (`2,4-6,9`)
 * and typing `2,4-6,9` lights up pages 2, 4, 5, 6 and 9. Keeping the arithmetic here — toggle, all, and the
 * text a set of pages compresses to — means the window and the tablet cannot disagree about it, and that
 * "tap a page twice and it goes away" is a tested sentence rather than a UI accident.
 *
 * Pages are 0-based inside the app and 1-based in the field, which is the file's own numbering as a reader
 * reads it. Every answer is **sorted**: an import selection is a set of source pages, brought in the file's
 * own order ([PageRanges.selection]).
 */
object ImportPageSelection {

    /** [page] toggled in [selected]: added in the file's order, or removed if it was already there. */
    fun toggle(selected: List<Int>, page: Int): List<Int> =
        if (page in selected) selected - page else (selected + page).sorted()

    /** Every page of a file of [pageCount] pages — *Select all*. */
    fun all(pageCount: Int): List<Int> = (0 until pageCount.coerceAtLeast(0)).toList()

    /**
     * The text the field shows for [selected]: a run of three or more collapses to `4-6`, anything shorter is
     * named page by page, so pages 2, 4, 5, 6 and 9 read `2,4-6,9` — the same grammar the reader can type
     * back ([PageRanges.selection] round-trips it).
     */
    fun text(selected: List<Int>): String {
        val pages = selected.filter { it >= 0 }.distinct().sorted()
        if (pages.isEmpty()) return ""
        val parts = ArrayList<String>()
        var runStart = pages.first()
        var previous = runStart
        for (page in pages.drop(1)) {
            if (page == previous + 1) {
                previous = page
                continue
            }
            parts.add(part(runStart, previous))
            runStart = page
            previous = page
        }
        parts.add(part(runStart, previous))
        return parts.joinToString(",")
    }

    private fun part(from: Int, to: Int): String =
        if (to - from >= 2) "${from + 1}-${to + 1}" else (from..to).joinToString(",") { "${it + 1}" }
}
