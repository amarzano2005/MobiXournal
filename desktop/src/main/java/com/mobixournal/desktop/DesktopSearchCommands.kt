package com.mobixournal.desktop

import com.mobixournal.render.SearchHit

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
    // Until the walk answers *this* query, the hits on hand belong to the previous one: stepping through
    // them would take the reader to a word they are not searching for (`DesktopTab.isSearchPending`).
    if (tab.isSearchPending) return false
    val answer = if (forward) tab.search.next() else tab.search.previous()
    if (answer.hits.isEmpty()) return false
    tab.search = answer
    answer.hit?.let { hit ->
        selectPage(tab, hit.pageIndex)
        revealHit(tab, hit)
    }
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
    revealHit(tab, hit)
    return true
}

/**
 * The sheet **moves to the match**, not only to its page.
 *
 * Going to a page put a word twenty lines down a long one off the bottom of the window, so "next match"
 * pointed at something the reader could not see. This is [Viewport.showing] with the window's own sizes:
 * the zoom a fit mode implies, the page's size, and the box the hit carries — the same rectangle the
 * highlight is painted in, so the page, the highlight and the scroll cannot disagree.
 */
private fun revealHit(tab: DesktopTab, hit: SearchHit) {
    val box = hit.boxes.firstOrNull() ?: return
    val viewW = tab.viewWidth
    val viewH = tab.viewHeight
    // No layout yet (a query answered before the canvas measured itself): there is nothing to scroll
    // against, and the page's own top is what the canvas is showing.
    if (viewW <= 0f || viewH <= 0f) return
    val page = runCatching { tab.document.page(hit.pageIndex) }.getOrNull() ?: return
    val pageW = page.width.toFloat()
    val pageH = page.height.toFloat()
    val zoom = tab.fit.zoomFor(pageW, pageH, viewW, viewH) ?: tab.viewport.zoom
    tab.viewport = tab.viewport.copy(zoom = zoom).showing(
        left = box.left.toFloat(),
        top = box.top.toFloat(),
        right = box.right.toFloat(),
        bottom = box.bottom.toFloat(),
        pageW = pageW,
        pageH = pageH,
        viewW = viewW,
        viewH = viewH,
    )
}

/** Put the search away — the field, the results list and the highlight all read the same three fields. */
internal fun DesktopShellState.closeSearch() {
    val tab = tabs.active ?: return
    tab.searchOpen = false
    tab.searchQuery = ""
    tab.search = DesktopSearch.EMPTY
}
