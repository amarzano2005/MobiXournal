package com.mobixournal.design

/** Inline document navigation: compact windows give the list the canvas space until a page is chosen. */
object DocumentNavigationLayout {
    const val COMPACT_WIDTH_DP = 600f
    const val BOOKMARKS_WIDTH_DP = 320f
    const val PAGES_WIDTH_DP = 320f
    const val PAGE_ROW_DP = 104f
    const val PREVIEW_HEIGHT_DP = 88f
    const val PREVIEW_WIDTH_DP = 132f
    const val PAGE_NUMBER_DP = 40f
    const val PAGE_CONTROL_DP = 40f

    data class PreviewSize(val width: Float, val height: Float)

    /** Fit the paper into its preview cell without changing its shape, including landscape PDFs. */
    fun previewSize(pageWidth: Double?, pageHeight: Double?): PreviewSize {
        val rawRatio = if (pageWidth != null && pageHeight != null && pageWidth.isFinite() && pageHeight.isFinite() &&
            pageWidth > 0 && pageHeight > 0) (pageWidth / pageHeight).toFloat() else 0.707f
        val ratio = rawRatio.takeIf { it.isFinite() && it > 0f } ?: 0.707f
        val width = (PREVIEW_HEIGHT_DP * ratio).coerceAtMost(PREVIEW_WIDTH_DP)
        return PreviewSize(width, width / ratio)
    }

    fun compact(workspaceWidthDp: Float): Boolean = workspaceWidthDp < COMPACT_WIDTH_DP

    /** Compact navigation replaces the visible canvas while the full-size canvas remains composed behind it. */
    fun width(workspaceWidthDp: Float, railWidthDp: Float, bookmarks: Boolean): Float {
        val available = (workspaceWidthDp - railWidthDp).coerceAtLeast(0f)
        return if (compact(workspaceWidthDp)) available else {
            (if (bookmarks) BOOKMARKS_WIDTH_DP else PAGES_WIDTH_DP).coerceAtMost(available)
        }
    }
}
