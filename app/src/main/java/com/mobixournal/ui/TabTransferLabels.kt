package com.mobixournal.ui

/**
 * What the two long-press actions on a tab are called, where the tab is going.
 *
 * The menu used to say "Move to other view" / "Mirror on other view" whether or not a split was
 * open, and the action silently opened one to have somewhere to put the tab. Naming the destination
 * — a numbered pane when split view is open, "split view" when it is not — makes the menu say what
 * the tap will do before it does it. Pure so the wording is pinned by a test rather than discovered
 * by long-pressing a tab in a hand-built layout.
 */
data class TabTransferLabels(
    /** Long-press action that hands the tab to the other pane, leaving this one. */
    val move: String,
    /** Long-press action that opens a second view of the tab in the other pane. */
    val mirror: String,
)

/** The labels for a tab in pane [paneIndex] (0-based) when [paneCount] panes are on screen. */
fun tabTransferLabels(paneIndex: Int, paneCount: Int): TabTransferLabels {
    if (paneCount <= 1) {
        return TabTransferLabels("Move to split view", "Mirror in split view")
    }
    // Panes are numbered 1-based in the strip's badge, so the destination is the other number.
    val other = if (paneIndex == 0) 2 else 1
    return TabTransferLabels("Move to pane $other", "Mirror in pane $other")
}
