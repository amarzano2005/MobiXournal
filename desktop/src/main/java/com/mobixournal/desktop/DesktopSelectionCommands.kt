package com.mobixournal.desktop

import com.mobixournal.render.SelectionEdit

/**
 * The two **commands** a selection answers, kept out of the shell — which is about the window, not about what
 * a marquee caught (`SourceStructureTest` keeps both small).
 *
 * They are the keyboard's side of the selection: `Delete` takes the caught elements away through the shared
 * [SelectionEdit], and `Esc` drops the outline. Both report whether they did anything, which is what makes
 * the key *unavailable* rather than a silent no-op — the state a header control greys a command out on.
 */

/** `Delete`: the caught elements go, as one edit and one undo step. */
internal fun deleteSelection(tab: DesktopTab): Boolean {
    val selection = tab.selection ?: return false
    if (selection.isEmpty) return false
    val index = tab.page
    val before = tab.document.snapshot()
    val emptied = SelectionEdit.delete(tab.document.page(index), selection)
    if (tab.document.editPage(index) { emptied } == null) return false
    tab.record(before)
    tab.selection = null
    return true
}

/** `Esc` while something is selected: the outline goes before anything else the key might mean. */
internal fun dismissSelection(tab: DesktopTab?): Boolean {
    if (tab?.selection == null) return false
    tab.selection = null
    return true
}
