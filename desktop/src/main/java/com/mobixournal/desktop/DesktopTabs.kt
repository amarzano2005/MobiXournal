package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    val document: DesktopDocument,
    /** What the strip shows; a double-click renames it. A tab is not the file, so a rename is a label. */
    title: String = document.file.name,
    page: Int = 0,
    viewport: Viewport = Viewport(),
    fit: FitMode = FitMode.AUTO,
) {
    var title: String by mutableStateOf(title)
    var page: Int by mutableStateOf(page)
    var viewport: Viewport by mutableStateOf(viewport)
    var fit: FitMode by mutableStateOf(fit)
    val file: File get() = document.file

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
