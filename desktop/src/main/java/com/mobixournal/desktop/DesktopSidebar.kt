package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The page sidebar's lane, and **whether it shows** — the one rule that decides it on its own.
 *
 * A one-sheet document has a list of one, and the window opened `Untitled 1` with 240 dp of thumbnail
 * standing for the page already filling the rest of it: a lane that told the reader nothing. The list is
 * worth its lane only when there is a list — **more than one page** ([pageListWorthShowing]) — so a
 * document with a single page opens with the canvas wider and the pages of a notebook open beside it.
 *
 * A manual `Ctrl+B` (or the menu's row) is remembered **for the document it was made on**: hiding the
 * pages of a forty-page notebook must not be undone by looking at another tab and back, and a one-page
 * file must not inherit a choice made on a different document. Nothing is written to the file — a lane
 * is a window's view, not a document's property.
 */
internal class DesktopSidebar {

    /** The answer the user gave on one document, keyed by the tab's own [DesktopTab.key]. */
    private var choice by mutableStateOf<Choice?>(null)

    /**
     * Whether the lane shows: the user's answer for this document, else [policy]'s rule.
     *
     * The setting is the **rule**, the toggle is one document's exception, and `Never` is the one answer a
     * toggle cannot override — a page list the reader has switched off entirely must not reappear because
     * `Ctrl+B` was pressed on a tab.
     */
    fun shows(tab: DesktopTab?, policy: SidebarPolicy = SidebarPolicy.DEFAULT): Boolean {
        if (policy == SidebarPolicy.NEVER) return false
        val chosen = choice
        if (tab != null && chosen?.key == tab.key) return chosen.visible
        return if (policy == SidebarPolicy.ALWAYS) true else pageListWorthShowing(tab?.pageCount)
    }

    /** Flip the lane for [tab], remembering the answer for that document only. */
    fun toggle(tab: DesktopTab?, policy: SidebarPolicy = SidebarPolicy.DEFAULT) {
        val visible = !shows(tab, policy)
        choice = if (tab == null) null else Choice(tab.key, visible)
    }

    private data class Choice(val key: String, val visible: Boolean)
}

/**
 * Whether a document of [pageCount] pages gets its page list by itself: **more than one**.
 *
 * That is the point at which the sidebar stops being a picture of the page in front of you and starts
 * being a way to reach the others. Nothing open is a zero — the startup notice has no pages to list.
 */
internal fun pageListWorthShowing(pageCount: Int?): Boolean = (pageCount ?: 0) > 1
