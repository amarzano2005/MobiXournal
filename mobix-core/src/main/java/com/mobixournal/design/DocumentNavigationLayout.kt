package com.mobixournal.design

/** Inline document navigation: compact windows give the list the canvas space until a page is chosen. */
object DocumentNavigationLayout {
    const val COMPACT_WIDTH_DP = 600f
    const val BOOKMARKS_WIDTH_DP = 320f
    const val PAGES_WIDTH_DP = 320f

    fun compact(workspaceWidthDp: Float): Boolean = workspaceWidthDp < COMPACT_WIDTH_DP

    /** Reserve one dp for the live canvas so opening navigation never disposes its document or undo stack. */
    fun width(workspaceWidthDp: Float, railWidthDp: Float, bookmarks: Boolean): Float {
        val available = (workspaceWidthDp - railWidthDp - 1f).coerceAtLeast(0f)
        return if (compact(workspaceWidthDp)) available else {
            (if (bookmarks) BOOKMARKS_WIDTH_DP else PAGES_WIDTH_DP).coerceAtMost(available)
        }
    }
}
