package com.mobixournal.render

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.withPage

/**
 * Tell the page window that the document replaced pages **behind its back**.
 *
 * Most edits already go through [windowEditPage], which keeps the window's own copy in step. A few
 * write a page straight into the model instead — an element placement built from `Document.pages`, a
 * parameter slider's drag, an element swap on the selected page — and those must not leave the window
 * describing the page they replaced: the *next* gesture would rebuild that page from the stale copy
 * and silently drop the write (a placed circuit vanishing under the pen, a table edit doing nothing).
 * The document is the truth, so `DrawingSurfaceView.doc`'s setter — the one place every edit lands —
 * calls this, which makes any such write safe whether or not its author knew about the window.
 */
internal fun DrawingSurfaceView.syncPageWindow(document: Document) = pageWindow.followDocument(document.pages)

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
    // One page of the document moved, so one page of it is rebuilt: `withPage` overlays the edit
    // instead of walking the list, which is what keeps a two-hundred-page notebook that was opened
    // page by page from being read in full the moment a stroke lands.
    doc = doc.withPage(index, edited)
    return edited
}
