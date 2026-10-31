package com.mobixournal.desktop

/**
 * The shell's **search commands**: what `Ctrl+F`, the field's keys and a click on a result row do.
 *
 * Extensions rather than methods (`SourceStructureTest` keeps `DesktopShell.kt` inside its budget, and the
 * shell is about the window, not about one field): every command reads the **active tab** and answers
 * whether it did anything, so a control can grey itself out on the answer. The state they edit lives on
 * the tab ([DesktopTab.searchQuery], [DesktopTab.search], [DesktopTab.searchOpen]) so the band, the results
 * list, the page highlight and the status counter cannot disagree about which match is current.
 */

/**
 * Open the search field on the active tab and hand it the keyboard (`Ctrl+F`) — **unavailable with no
 * tab**, the state the band and the menu draw the command greyed out on.
 */
internal fun DesktopShellState.find(): Boolean {
    val tab = tabs.active ?: return false
    tab.searchOpen = true
    return true
}

/**
 * The field's text, kept on the tab and answered by [SearchRunner] off the UI thread.
 *
 * Empting the field empties its answer **here**, synchronously: the reader sees the results and the page
 * highlight go with the query on the press, not a frame later when the walk returns.
 */
internal fun DesktopShellState.setSearchQuery(query: String) {
    val tab = tabs.active ?: return
    tab.searchQuery = query
    if (query.isEmpty()) tab.search = DesktopSearch.EMPTY
}

/** Step to the next (or previous) match and go to its page — `Enter` and `Shift+Enter` in the field. */
internal fun DesktopShellState.stepSearch(forward: Boolean): Boolean {
    val tab = tabs.active ?: return false
    val answer = if (forward) tab.search.next() else tab.search.previous()
    if (answer.hits.isEmpty()) return false
    tab.search = answer
    answer.hit?.let { hit -> selectPage(tab, hit.pageIndex) }
    return true
}

/**
 * Stand on match [index] — a click on a result row: the highlight and the page move together, and the match
 * becomes the one `Enter` then steps away from.
 */
internal fun DesktopShellState.showSearchHit(index: Int): Boolean {
    val tab = tabs.active ?: return false
    val answer = tab.search.at(index)
    val hit = answer.hit ?: return false
    tab.search = answer
    selectPage(tab, hit.pageIndex)
    return true
}

/** Put the search away — the field, the results list and the highlight all read the same three fields. */
internal fun DesktopShellState.closeSearch() {
    val tab = tabs.active ?: return
    tab.searchOpen = false
    tab.searchQuery = ""
    tab.search = DesktopSearch.EMPTY
}
