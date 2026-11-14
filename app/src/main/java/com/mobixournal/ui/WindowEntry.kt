package com.mobixournal.ui

/**
 * Whether the window **is** the entry screen: no document open in any pane the reader can see.
 *
 * The question is about the panes **on screen**, not about every pane that exists. In single-pane mode
 * only the left pane is drawn, and closing split view deliberately keeps the right pane's tabs in memory
 * for the next split (`EditorPane`). Counting those answered "still open" for a window whose only visible
 * pane was empty, so the entry page arrived with the top bar, the tab strip and the rail still drawn around
 * it — a home screen with a toolbar over it.
 *
 * In split view the answer is the other way round: **one** document anywhere on screen is a workspace, so
 * focusing an empty half can never swap the window out from under the tap that only meant "the tools go
 * here".
 */
internal fun windowIsEmpty(paneTabs: List<TabsUiState>, splitView: Boolean): Boolean = if (splitView) {
    paneTabs.none { it.titles.isNotEmpty() }
} else {
    // The pane that is drawn first, and the only one when the split is off.
    paneTabs.firstOrNull()?.titles?.isEmpty() ?: true
}
