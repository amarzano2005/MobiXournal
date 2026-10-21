package com.mobixournal.desktop

/**
 * How far a page may be panned on one axis — the arithmetic behind "the page cannot be dragged off the
 * window, and a page that fits is centred".
 *
 * It is separate from [Viewport] because the four cases that matter are the four that are easy to get
 * wrong and impossible to check by eye: the first page of a document (nothing above it), the last page
 * (nothing below), a page **taller than the viewport** (a real scroll range), a page shorter than the
 * viewport (no range at all: centred), and an empty document (no page, no range, offset 0).
 */
object PageIndexScroll {

    /**
     * The allowed offset range for content [contentPx] long inside a [viewportPx] viewport.
     *
     * The range is expressed in the same convention as the paint offset: `0` puts the content's start at
     * the viewport's start, and the negative end scrolls the content's end to the viewport's end. A
     * content that fits gets the **single** value that centres it, so a centred page stays centred.
     */
    fun range(contentPx: Float, viewportPx: Float): ClosedFloatingPointRange<Float> {
        if (contentPx <= 0f || viewportPx <= 0f) return 0f..0f
        val slack = viewportPx - contentPx
        return if (slack >= 0f) {
            // Centred: one legal value, and clamping keeps it there.
            val centred = slack / 2f
            centred..centred
        } else {
            slack..0f
        }
    }

    /** [offset] pulled into the legal range for content [contentPx] in a [viewportPx] viewport. */
    fun clamp(offset: Float, contentPx: Float, viewportPx: Float): Float {
        val range = range(contentPx, viewportPx)
        return offset.coerceIn(range.start, range.endInclusive)
    }

    /** True when the content's start is at the viewport's start and there is more of it to show. */
    fun isAtStart(offset: Float, contentPx: Float, viewportPx: Float): Boolean =
        contentPx > viewportPx && offset >= -0.5f

    /** True when the content's end is at the viewport's end. */
    fun isAtEnd(offset: Float, contentPx: Float, viewportPx: Float): Boolean =
        contentPx > viewportPx && offset <= viewportPx - contentPx + 0.5f

    /** The offset that shows page [pageIndex]'s top in a stack of [pageHeightsPx], each after the last. */
    fun offsetOfPage(pageIndex: Int, pageHeightsPx: List<Float>, gapPx: Float, viewportPx: Float): Float {
        if (pageIndex !in pageHeightsPx.indices) return 0f
        val before = pageHeightsPx.take(pageIndex).sumOf { it.toDouble() }.toFloat() + gapPx * pageIndex
        val total = pageHeightsPx.sumOf { it.toDouble() }.toFloat() + gapPx * (pageHeightsPx.size - 1).coerceAtLeast(0)
        return clamp(-before, total, viewportPx)
    }

    /**
     * Which page occupies the viewport's centre given the stack described above — the inverse used by
     * the status bar and the sidebar's highlight.
     */
    fun pageAt(offset: Float, pageHeightsPx: List<Float>, gapPx: Float, viewportPx: Float): Int {
        if (pageHeightsPx.isEmpty()) return 0
        val centre = -offset + viewportPx / 2f
        var top = 0f
        for ((index, height) in pageHeightsPx.withIndex()) {
            val bottom = top + height
            if (centre < bottom) return index
            top = bottom + gapPx
        }
        return pageHeightsPx.lastIndex
    }
}
