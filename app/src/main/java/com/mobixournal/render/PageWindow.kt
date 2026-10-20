package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.EditedPages
import com.mobixournal.format.model.Page
import java.util.Collections

/**
 * The canvas's **editable** page window: a bounded read cache over a [PageSource], plus an
 * **overlay of edited pages that is never evicted** (`docs/data-path.md` → *The page window*).
 *
 * The read cache is what keeps a 200-page notebook's working set at the few pages on screen: a page
 * is materialised through the source when it is asked for, and the least recently used page falls out
 * of the cache as others come in. An **edit** goes through [edit] (or [set]), which
 * - materialises the page it touches first — so a command may act on a page the window has evicted,
 *   without the caller knowing or caring which pages are resident;
 * - keeps the result in the *overlay*, which eviction never touches, so the edit survives the page
 *   leaving the cache — which is what lets undo, redo and a save still find it;
 * - records the index as [dirty], so **it** knows which pages moved ([dirtyIndices]).
 *
 * That record is the window's own, and a **save does not read it**: which pages a save may copy out of
 * the file the document came from is derived from the shape of the document's page list instead — a page
 * an edit did not replace is still the source's own (`format/mobix/MobixPageReuse.kt`). A flag is a
 * second copy of the answer, and a page-list edit that never went through the window would be missing
 * from it; the overlay itself cannot be, because it *is* the edit.
 *
 * Pinning ([pin]) keeps a page in the *read* cache for as long as a gesture touches it — a live
 * resize re-reads the page every frame, and re-inflating it per frame would be the very cost the
 * window exists to remove. Pinned pages are not evictable; the overlay is never evictable either.
 *
 * The window owns no document: it is a view of a source, and an edit it holds is written into the
 * model by the caller ([applyTo]). [reset] is the inverse contract — call it whenever the document is
 * replaced wholesale (load, undo/redo, a mirrored pane, a page-list edit), because then the overlay
 * describes pages the document no longer has.
 */
internal class PageWindow(
    private val source: PageSource,
    /** How many *unedited* pages the read cache may hold at once. */
    private val budget: Int = DEFAULT_BUDGET,
) {

    init {
        require(budget > 0) { "the page window needs a positive budget, got $budget" }
    }

    /** How many pages the document declares (answered by the source, never by opening a page). */
    val pageCount: Int get() = source.pageCount

    /**
     * Materialised, *unedited* pages, least recently used first. Access-ordered so [page] refreshes
     * an entry's position; an entry above [budget] is dropped.
     */
    private val cache = object : LinkedHashMap<Int, Page>(budget, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Page>?): Boolean {
            val index = eldest?.key ?: return false
            return size > budget && index !in pinned
        }
    }

    /** Pages an edit produced, by index — kept for the whole window's life, never evicted. */
    private val overlay = LinkedHashMap<Int, Page>()

    // The overlay is what a save reads — through the document the edit was written into
    // (`format/mobix/MobixPageReuse.kt`). The accessors below are the window's own account of itself,
    // for a diagnostic or an editor that wants to know which pages it has moved: the save derives the
    // same answer from the page list, so that one can never go stale.

    /** Pages a live gesture is touching; they stay in the read cache until it settles. */
    private val pinned = Collections.synchronizedSet(mutableSetOf<Int>())

    /** The edited pages, in the order they were first edited. */
    val dirtyPages: Map<Int, Page> get() = overlay.toMap()

    /** The indices of the pages an edit produced. */
    val dirtyIndices: Set<Int> get() = overlay.keys.toSet()

    /** True when nothing has been edited; the window is then a pure read-through view. */
    val isClean: Boolean get() = overlay.isEmpty()

    /**
     * The page at [index]: the edit if one was made, otherwise the cached page, otherwise the page
     * materialised from the source. Refreshes the cache's recency.
     *
     * @throws IllegalArgumentException when [index] is out of range.
     */
    @Synchronized
    fun page(index: Int): Page {
        overlay[index]?.let { return it }
        cache[index]?.let { return it }
        val page = source.page(index)
        cache[index] = page
        return page
    }

    /** The page at [index], or null when the index is out of range. */
    fun pageOrNull(index: Int): Page? = if (index in 0 until pageCount) page(index) else null

    /**
     * Apply [transform] to page [index] as one edit: materialise it, transform it, and keep the
     * result. Returns the new page, or null when [transform] handed back the same instance (a no-op
     * command), in which case nothing is marked dirty.
     *
     * The page is left in the overlay, so the edit is still there after the read cache has moved on.
     */
    fun edit(index: Int, transform: (Page) -> Page): Page? {
        val before = page(index)
        val after = transform(before)
        if (after === before) return null
        set(index, after)
        return after
    }

    /**
     * Write [page] at [index] as an edit — the half [edit] builds on, and what undo/redo call to put a
     * page back. Also the way an edit made elsewhere (a pure op over a page) lands in the window.
     */
    @Synchronized
    fun set(index: Int, page: Page) {
        require(index in 0 until pageCount) { "page index $index out of bounds (0..${pageCount - 1})" }
        overlay[index] = page
        cache.remove(index)
    }

    /**
     * Drop the edit at [index], so the window answers with the source's page again — the undo of an
     * edit on a page that has since left the read cache. Returns the page the source now holds.
     */
    @Synchronized
    fun revert(index: Int): Page? {
        overlay.remove(index)
        cache.remove(index)
        return pageOrNull(index)
    }

    /** Keep [index] in the read cache until [unpin] (a live gesture re-reads it every frame). */
    fun pin(index: Int) {
        pinned.add(index)
    }

    /** Let [index] be evicted again once the gesture that pinned it has settled. */
    fun unpin(index: Int) {
        pinned.remove(index)
    }

    /** True when page [index] is in hand right now — cached, edited or pinned. */
    @Synchronized
    fun isMaterialized(index: Int): Boolean = overlay.containsKey(index) || cache.containsKey(index)

    /** The indices currently in hand — edited or cached — for a test or a diagnostic. */
    @Synchronized
    fun materializedIndices(): Set<Int> = overlay.keys + cache.keys

    /** The indices in the **read** cache only, edited pages excluded (a test or a diagnostic). */
    @Synchronized
    fun cachedIndices(): Set<Int> = cache.keys.toSet()

    /** Forget every edit and every cached page; the source is the truth again. */
    @Synchronized
    fun reset() {
        overlay.clear()
        cache.clear()
        pinned.clear()
    }

    /**
     * Re-point the window at [pages], the page list the document holds **now**.
     *
     * Every page write is supposed to go through [edit] or [set], and when it does the overlay holds
     * the very page the document holds, so this finds nothing to do. A write that does not — a
     * placement helper that builds its page from `Document.pages`, a slider drag, an element swap on
     * the selected page — leaves the overlay (and any cached page) describing the page the document
     * had *before* it, and the next edit through the window would rebuild the page from that stale
     * copy and silently drop the write: the element the user just placed is gone again.
     *
     * So the document is the truth and the window follows it: a page the document replaced is taken
     * over, a page it did not replace is left in hand (and never even indexed, so a reader-backed
     * document is not inflated by this check), and an index the document no longer has is dropped (a
     * page-list edit; [reset] is the fuller answer a structural change calls).
     *
     * @param pages The document's own page list, as `Document.pages` answers it.
     */
    @Synchronized
    fun followDocument(pages: List<Page>) {
        if (overlay.isEmpty() && cache.isEmpty()) return
        // An `EditedPages` view says exactly which indices a write replaced; anything else is a plain
        // list, where every index is a list read.
        val replaced = (pages as? EditedPages)?.editedIndices
        for (index in overlay.keys.toList()) {
            if (index !in pages.indices) {
                overlay.remove(index)
                continue
            }
            if (replaced != null && index !in replaced) continue
            val written = pages[index]
            if (written !== overlay[index]) overlay[index] = written
        }
        for (index in cache.keys.toList()) {
            if (index !in pages.indices) {
                cache.remove(index)
                continue
            }
            if (replaced != null && index !in replaced) continue
            if (pages[index] !== cache[index]) cache.remove(index)
        }
    }

    /**
     * The whole document's page list with this window's edits applied over [pages] — the read the
     * caller uses to keep a [Document] consistent with the window, **touching only the edited pages**
     * so an edit never drags the rest of a 200-page notebook into memory.
     */
    fun applyTo(pages: List<Page>): List<Page> {
        if (overlay.isEmpty()) return pages
        val out = pages.toMutableList()
        for ((index, page) in overlay) if (index in out.indices) out[index] = page
        return out
    }

    /** [document] with this window's edits applied over its pages. */
    fun applyTo(document: Document): Document = document.copy(pages = applyTo(document.pages))

    companion object {
        /** The read budget when the caller has no reason to pick another (current page ± 2). */
        const val DEFAULT_BUDGET = 5
    }
}
