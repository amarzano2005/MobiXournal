package com.mobixournal.desktop

/**
 * What the **advanced page management** panel asks, and what the answers mean.
 *
 * The fields hold text because that is what a reader types, and every rule that turns it into page indices
 * lives here — pure, and the one place the panel's enabled buttons and the commands behind them read. A
 * number the panel refuses is a number the command cannot act on, so the two can never disagree about what
 * "pages 3 to 7" means.
 */
internal data class PageRangeRequest(
    val first: String = "",
    val last: String = "",
    val target: String = "",
)

/** A typed answer, as 0-based inclusive indices, or the sentence saying why it cannot be used. */
internal sealed interface PageRangePlan {
    data class Pages(val first: Int, val last: Int) : PageRangePlan

    data class Invalid(val message: String) : PageRangePlan
}

internal object PageRanges {

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

    /** The request the panel opens on: the page in view, moved to the front until told otherwise. */
    fun opening(pageCount: Int, current: Int): PageRangeRequest {
        val at = (current + 1).coerceIn(1, pageCount.coerceAtLeast(1))
        return PageRangeRequest(first = "$at", last = "$at", target = "1")
    }

    private fun numbering(pageCount: Int): String = "Pages are numbered 1 to $pageCount."
}
