package com.mobixournal.render

import com.mobixournal.format.model.Page

/**
 * Drop the page window's kept pages and cached pages: the document was replaced wholesale (a load,
 * undo/redo, a mirrored pane, a page-list edit), so the window describes a document that is gone.
 */
internal fun DrawingSurfaceView.resetPageWindow() = pageWindow.reset()

/**
 * Apply [transform] to page [index] **through the page window** and adopt the result as one model
 * change (the caller owns history and repainting, as everywhere else in the canvas).
 *
 * This is how a command reaches a page it is not currently showing: [PageWindow.edit] materialises the
 * page first, so an edit on a page the window has evicted works like one on the page in view, and the
 * result stays in the window so undo, redo and the save still find it after the cache has moved on.
 *
 * @return the page the edit produced, or null when [index] is out of range or [transform] changed
 *   nothing (the same instance handed back), in which case the document is left alone.
 */
internal fun DrawingSurfaceView.windowEditPage(index: Int, transform: (Page) -> Page): Page? {
    if (index !in 0 until pageWindow.pageCount) return null
    val edited = pageWindow.edit(index, transform) ?: return null
    doc = doc.copy(pages = doc.pages.toMutableList().also { it[index] = edited })
    return edited
}
