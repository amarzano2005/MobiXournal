package com.mobixournal.render

/**
 * What a **page range** question asks, and what the answers mean — one rule for both shells.
 *
 * The fields hold text because that is what a reader types, and every rule that turns them into page
 * indices lives here: pure, and the one place an enabled button and the command behind it read. A number
 * a shell refuses is a number its command cannot act on, so whether that is the window's Manage pages
 * panel or the tablet's Import PDF card, "pages 3 to 7" means the same thing.
 *
 * [target] is only meaningful to a move; an import ignores it.
 */
data class PageRangeRequest(
    val first: String = "",
    val last: String = "",
    val target: String = "",
)

/** A typed answer, as 0-based inclusive indices, or the sentence saying why it cannot be used. */
sealed interface PageRangePlan {
    data class Pages(val first: Int, val last: Int) : PageRangePlan

    /**
     * Several runs of pages, deduplicated and in the source's own order — what `2,4-6,9` names.
     *
     * A selection is a **set** of source pages, not a sequence the reader dictates: a PDF's pages land in
     * the document in the order the file has them, so typing `9,2` brings page 2 and then page 9.
     */
    data class Chosen(val indices: List<Int>) : PageRangePlan

    data class Invalid(val message: String) : PageRangePlan

    /**
     * Every page the answer names, 0-based, in the order the plan acts on — or null when it is refused.
     * The one accessor a card and the command behind it share, so a single run and a selection behave alike.
     */
    fun indices(): List<Int>? = when (this) {
        is Pages -> (first..last).toList()
        is Chosen -> indices
        is Invalid -> null
    }
}

object PageRanges {

    /** The range the fields name. */
    fun pages(request: PageRangeRequest, pageCount: Int): PageRangePlan {
        if (pageCount <= 0) return PageRangePlan.Invalid("There are no pages to work on.")
        val first = request.first.trim()
        val last = request.last.trim()
        if (first.isEmpty() || last.isEmpty()) {
            return PageRangePlan.Invalid("Type the first and the last page of the range.")
        }
        val from = first.toIntOrNull()
        val to = last.toIntOrNull()
        if (from == null || to == null) return PageRangePlan.Invalid(numbering(pageCount))
        if (from !in 1..pageCount || to !in 1..pageCount) return PageRangePlan.Invalid(numbering(pageCount))
        if (to < from) return PageRangePlan.Invalid("The last page of the range comes before the first.")
        return PageRangePlan.Pages(from - 1, to - 1)
    }

    /**
     * The pages a **selection** names — `2,4-6,9`, and `3` or `3-5` as the one-run case of it.
     *
     * Imports are the reason this exists: a chapter of a scan is rarely one run, and the tablet could only
     * name a first and a last page. The grammar is the reader's own — comma-separated runs, either end
     * included, spaces free — and the answer is a set: duplicates collapse and the pages come back in the
     * source's order, because that is how the document is going to hold them. Everything else (a letter, a
     * page past the end, a run typed backwards) is a sentence saying what to type instead, never a guess.
     *
     * The contiguous [pages] API is untouched: Manage pages still asks for one run, because a drag of pages
     * or a delete can only mean one.
     */
    fun selection(text: String, pageCount: Int): PageRangePlan {
        if (pageCount <= 0) return PageRangePlan.Invalid("There are no pages to work on.")
        val typed = text.trim()
        if (typed.isEmpty()) return PageRangePlan.Invalid("Type the pages to bring, like 2,4-6,9.")
        val chosen = sortedSetOf<Int>()
        for (part in typed.split(',')) {
            val run = part.trim()
            if (run.isEmpty()) return PageRangePlan.Invalid("Type the pages to bring, like 2,4-6,9.")
            val ends = run.split('-')
            if (ends.size > 2) return PageRangePlan.Invalid("A run of pages reads like 4-6.")
            val from = ends[0].trim().toIntOrNull() ?: return PageRangePlan.Invalid(numbering(pageCount))
            val to = if (ends.size == 2) {
                ends[1].trim().toIntOrNull() ?: return PageRangePlan.Invalid(numbering(pageCount))
            } else {
                from
            }
            if (from !in 1..pageCount || to !in 1..pageCount) {
                return PageRangePlan.Invalid(numbering(pageCount))
            }
            if (to < from) return PageRangePlan.Invalid("The last page of $run comes before the first.")
            for (page in from..to) chosen.add(page - 1)
        }
        return PageRangePlan.Chosen(chosen.toList())
    }

    /** The page the range should **start on** after a move. */
    fun target(request: PageRangeRequest, pageCount: Int): PageRangePlan {
        if (pageCount <= 0) return PageRangePlan.Invalid("There are no pages to work on.")
        val typed = request.target.trim()
        if (typed.isEmpty()) return PageRangePlan.Invalid("Type the page the range should start on.")
        val page = typed.toIntOrNull()
        if (page == null || page !in 1..pageCount) return PageRangePlan.Invalid(numbering(pageCount))
        return PageRangePlan.Pages(page - 1, page - 1)
    }

    /** Why a **move** cannot happen, or null when it can. */
    fun moveRefusal(range: PageRangePlan, target: PageRangePlan): String? {
        if (range !is PageRangePlan.Pages || target !is PageRangePlan.Pages) return null
        if (target.first in range.first..range.last) return "The range already starts on that page."
        return null
    }

    /** Why a **delete** cannot happen, or null when it can — the document always keeps a page. */
    fun deleteRefusal(range: PageRangePlan, pageCount: Int): String? {
        if (range !is PageRangePlan.Pages) return null
        if (range.last - range.first + 1 >= pageCount) return "The whole document cannot be deleted."
        return null
    }

    /** The selection a card opens on: **every** page of the file, as the reader would type it. */
    fun whole(pageCount: Int): String = if (pageCount <= 1) "1" else "1-$pageCount"

    /** The request a panel opens on: the page in view, moved to the front until told otherwise. */
    fun opening(pageCount: Int, current: Int): PageRangeRequest {
        val at = (current + 1).coerceIn(1, pageCount.coerceAtLeast(1))
        return PageRangeRequest(first = "$at", last = "$at", target = "1")
    }

    private fun numbering(pageCount: Int): String = "Pages are numbered 1 to $pageCount."
}
