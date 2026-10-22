package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.render.EditHistory
import com.mobixournal.render.Uncompilable
import java.io.File

/**
 * One open document and **its own view state** — page, zoom, pan, fit mode.
 *
 * Per-tab state is the point of tabs on a workstation: checking a formula against a chapter must not
 * move the chapter, and coming back to a tab has to land where you left it. The document itself owns a
 * file handle (a `.mobix` reader), so closing a tab closes it ([DesktopTabs.close]).
 *
 * The view state starts as [FitMode.AUTO], which shows a sheet whole and fits a long strip to the width
 * so its writing stays readable (`FitMode.resolve`); every zoom key sets a mode explicitly.
 *
 * The fields are **snapshot state**: the strip, the canvas and the status bar read them, so a wheel zoom
 * or a page change has to invalidate those readers or nothing on screen would move. Snapshot state also
 * works outside a composition, which is what keeps the rules below ordinary JVM tests.
 */
class DesktopTab(
    document: DesktopDocument,
    /** What the strip shows; a double-click renames it. A tab is not the file, so a rename is a label. */
    title: String = document.file.name,
    page: Int = 0,
    viewport: Viewport = Viewport(),
    fit: FitMode = FitMode.AUTO,
) {
    /**
     * The document this tab shows. A Save As **re-homes** the tab onto the file it just wrote, so the
     * next Save writes back to it — and the loader's copy of the old one is closed with it.
     */
    var document: DesktopDocument by mutableStateOf(document)
        private set

    var title: String by mutableStateOf(title)
    var page: Int by mutableStateOf(page)
    var viewport: Viewport by mutableStateOf(viewport)
    var fit: FitMode by mutableStateOf(fit)

    /**
     * Layers hidden **in this window**, by page index. Never written to the document.
     *
     * A layer the user has switched off is a view state, not an edit: hiding an annotation to read the
     * page underneath must not change the file, and it must not survive a reopen either. It is per tab
     * because two tabs may be looking at the same document for two different reasons, and per page
     * because "Layer 2" of page 1 is not "Layer 2" of page 7.
     */
    private val hidden = mutableStateMapOf<Int, Set<Int>>()

    /**
     * This tab's **own** undo history, as `EditHistory` says it should be: one per editor, one per tab
     * (`docs/ui-modernization.md` §3). A snapshot is the document at one instant — the immutable model
     * plus the page window's overlay — so a step costs the pages the session touched, not the notebook,
     * and undo of an untouched page never reads it.
     *
     * Every command records **one** step, taken before it changes anything: a stroke records on its
     * lift and an eraser drag records on its first bite, never per pointer sample. Recording a new step
     * discards the redo branch, as in every editor.
     */
    private val history = EditHistory<DesktopDocument.Snapshot>()

    /** True while there is a step to undo — false is what greys the command out. */
    var canUndo: Boolean by mutableStateOf(false)
        private set

    /** True while an undone step can be re-applied. */
    var canRedo: Boolean by mutableStateOf(false)
        private set

    /** The layers switched off on [page] — the set the painter and the thumbnails skip. */
    fun hiddenOn(page: Int): Set<Int> = hidden[page].orEmpty()

    /** Whether [layer] of [page] is switched off. */
    fun isLayerHidden(page: Int, layer: Int): Boolean = hidden[page]?.contains(layer) == true

    /** Switches [layer] of [page] off or back on; the document is untouched. */
    fun toggleLayer(page: Int, layer: Int) {
        val current = hidden[page].orEmpty()
        hidden[page] = if (layer in current) current - layer else current + layer
    }

    val file: File get() = document.file

    /**
     * Point this tab at [target], which a Save As has just written: the file is re-opened (the write
     * produced it, so it is the truth), the previous document is closed, and the tab's label follows
     * the new name. The page and viewport stay where they were — a Save As is not a new document.
     */
    fun rehome(target: File) {
        if (target.absoluteFile == document.file.absoluteFile) return
        val reloaded = DocumentLoader.load(target)
        val previous = document
        document = reloaded
        title = target.name
        // The write just made the new file the truth, and the snapshots name pages of the archive
        // that was closed; a history across a Save As would undo onto a document that no longer is one.
        forgetHistory()
        previous.close()
    }

    /**
     * Record the state **before** one command, so `Ctrl+Z` returns to it. Called by the tool that is
     * about to change the page — once per gesture, never per sample.
     */
    fun record(before: DesktopDocument.Snapshot) {
        history.record(before)
        syncHistory()
    }

    /** Undo the last command of **this tab**, restoring the page window with it. False when empty. */
    fun undo(): Boolean {
        val previous = history.undo(document.snapshot()) ?: return false
        document.restore(previous)
        syncHistory()
        return true
    }

    /** Re-apply the step [undo] took back. False when there is nothing to redo. */
    fun redo(): Boolean {
        val next = history.redo(document.snapshot()) ?: return false
        document.restore(next)
        syncHistory()
        return true
    }

    /** The two flags the commands and a header control read, kept in step with the stacks. */
    private fun syncHistory() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    /** Drop every step: the document was replaced wholesale (a save, a Save As, a closed archive). */
    private fun forgetHistory() {
        history.clear()
        syncHistory()
    }

    /**
     * Write this tab's document back over its own file (`Ctrl+S`).
     *
     * The write is [DesktopSaves.stage]: it produces the bytes while the document can still be read (a
     * `.mobix` repack streams its unchanged entries out of that very archive), then this releases the
     * archive, moves the staged file into place and opens the file again — the order Windows requires,
     * since it will not replace a file this process still holds open. The tab keeps its page, viewport
     * and label: what comes back is the same document, read from the file that now holds the edit.
     */
    fun saveBack(): List<Uncompilable> {
        val target = file
        val staged = DesktopSaves.stage(document, target)
        val failure = try {
            document.close()
            staged.commit()
            null
        } catch (thrown: Throwable) {
            thrown
        } finally {
            staged.close()
        }
        // The file is read again whether the move happened or not: after a failed move it is still the
        // original, and a tab must never be left holding a document whose handle it already released.
        document = DocumentLoader.load(target)
        // The closed archive's pages are what the old steps would restore; after a save the file is the
        // new baseline, so the history starts here (and `desktop-dirty-state-*` will move the save point).
        forgetHistory()
        failure?.let { throw it }
        return staged.uncompiled
    }

    /** The identity two opens of the same document share — the absolute path, not the label. */
    val key: String get() = DesktopTabs.keyOf(document.file)

    val pageCount: Int get() = document.pageCount

    /** The zoom actually used: the fitted one while a fit mode is on, the user's own otherwise. */
    fun effectiveZoom(pageW: Float, pageH: Float, viewW: Float, viewH: Float): Float =
        fit.zoomFor(pageW, pageH, viewW, viewH) ?: viewport.zoom

    /** The mode the status bar names: [FitMode.AUTO] resolved against this page's shape. */
    fun fitFor(pageW: Float, pageH: Float): FitMode = fit.resolve(pageW, pageH)

    override fun toString(): String = "DesktopTab($title, page=$page, fit=$fit)"
}

/**
 * The open documents, the active one, and the operations a strip and a keyboard need — a plain state
 * class with no Compose types in it, so every rule below is a JVM test rather than a window to poke at.
 *
 * The list and the active index are snapshot state too, for the same reason: opening, closing and
 * switching documents are exactly the moments the strip must be redrawn.
 *
 * The rules worth stating:
 *  - opening a file that is already open **activates its tab** instead of opening a second view of it
 *    (and closes the document the caller just loaded, so no file handle leaks);
 *  - closing a tab activates a **neighbour** — the next one, or the previous one when the last tab closed;
 *  - closing the last tab empties the shell;
 *  - reordering never changes which tab is active.
 */
class DesktopTabs {

    private val tabs = mutableStateListOf<DesktopTab>()

    /** Index of the active tab, or -1 when nothing is open. */
    var activeIndex: Int by mutableStateOf(-1)
        private set

    val all: List<DesktopTab> get() = tabs
    val size: Int get() = tabs.size
    val isEmpty: Boolean get() = tabs.isEmpty()
    val active: DesktopTab? get() = tabs.getOrNull(activeIndex)

    /**
     * Add [document] as a tab and activate it. When the same file is already open, that tab is
     * activated and [document] is closed — the caller has just decoded a document it no longer needs.
     */
    fun open(document: DesktopDocument): DesktopTab {
        val key = keyOf(document.file)
        val existing = tabs.indexOfFirst { it.key == key }
        if (existing >= 0) {
            document.close()
            activeIndex = existing
            return tabs[existing]
        }
        val tab = DesktopTab(document)
        tabs += tab
        activeIndex = tabs.lastIndex
        return tab
    }

    /** Close tab [index], closing its document. Returns true when the shell still has a tab open. */
    fun close(index: Int): Boolean {
        if (index !in tabs.indices) return tabs.isNotEmpty()
        val closing = tabs.removeAt(index)
        closing.document.close()
        activeIndex = when {
            tabs.isEmpty() -> -1
            index < activeIndex -> activeIndex - 1
            index == activeIndex && index >= tabs.size -> tabs.lastIndex
            else -> activeIndex.coerceAtMost(tabs.lastIndex)
        }
        return tabs.isNotEmpty()
    }

    /** Close the active tab; returns true when a document is still open. */
    fun closeActive(): Boolean = close(activeIndex)

    fun activate(index: Int): DesktopTab? {
        if (index !in tabs.indices) return null
        activeIndex = index
        return tabs[index]
    }

    fun activate(tab: DesktopTab): DesktopTab? = activate(tabs.indexOf(tab))

    /** The next tab (Ctrl+Tab), wrapping — null when there is nothing to switch to. */
    fun next(): DesktopTab? {
        if (tabs.isEmpty()) return null
        activeIndex = (activeIndex + 1).mod(tabs.size)
        return tabs[activeIndex]
    }

    /** The previous tab (Ctrl+Shift+Tab), wrapping. */
    fun previous(): DesktopTab? {
        if (tabs.isEmpty()) return null
        activeIndex = (activeIndex - 1).mod(tabs.size)
        return tabs[activeIndex]
    }

    /**
     * Move tab [from] to [to], keeping the **same** tab active. A drag that lands out of range is
     * clamped rather than dropped: letting go slightly past the strip must not lose the tab.
     */
    fun move(from: Int, to: Int) {
        if (from !in tabs.indices) return
        val target = to.coerceIn(0, tabs.lastIndex)
        if (target == from) return
        val moving = tabs.removeAt(from)
        tabs.add(target, moving)
        val active = activeIndex
        activeIndex = when {
            active == from -> target
            from < active && target >= active -> active - 1
            from > active && target <= active -> active + 1
            else -> active
        }
    }

    /** Close every tab (the shell is shutting down). */
    fun closeAll() {
        for (tab in tabs) tab.document.close()
        tabs.clear()
        activeIndex = -1
    }

    companion object {

        /** The identity of an open document: its absolute path, normalised so `./a.xopp` == `a.xopp`. */
        fun keyOf(file: File): String = runCatching {
            file.canonicalFile.path
        }.getOrElse { file.absoluteFile.normalize().path }
    }
}
