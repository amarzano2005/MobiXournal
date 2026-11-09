package com.mobixournal

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import com.mobixournal.format.model.Page
import com.mobixournal.tabs.TabSession
import com.mobixournal.ui.entry.EntryCards
import com.mobixournal.ui.entry.EntryPreviews
import com.mobixournal.ui.entry.RecentNotebook
import com.mobixournal.ui.entry.RecentNotebookStore

// --- the entry screen's memory ---------------------------------------------------------------
//
// Two lists the screen draws, and the offer it makes: the notebooks opened lately on this device, and
// the tabs the last run left open — shown, never reopened, until the reader takes them back. They live
// here rather than in `MainActivityDocuments` because they are one subject (what the app remembers
// about a document once it has been read) and because that file is at its structural ceiling.

/**
 * The entry screen's state: the [recent] notebooks and the tabs the last run left open ([previous]).
 *
 * The second is an **offer**. A pane with nothing open says what was being edited and lets the reader
 * take it back (*Restore last session*, or one card per tab) instead of reopening it behind their
 * back; the empty pane that is offering it must not write over it either, which is what
 * `EditorPane.holdsPreviousSession` holds off.
 */
internal class EntryRecents(private val context: Context) {

    private val store = RecentNotebookStore(context)

    /** The notebooks opened lately, newest first. */
    val recent = mutableStateOf<List<RecentNotebook>>(emptyList())

    /** The tabs the last run left open in the focused pane, or null when it left none. */
    val previous = mutableStateOf<TabSession?>(null)

    /** Read the list back from the device — the launch path. */
    fun reload() {
        recent.value = store.list()
    }

    /** Put a just-opened document at the head of the list. */
    fun remember(uri: Uri, name: String) {
        recent.value = store.record(uri, name)
    }

    /** Take a plain card off the list, and its picture with it. */
    fun forget(notebook: RecentNotebook) {
        recent.value = store.forget(notebook.uri)
        EntryPreviews.forget(context, notebook.uri)
    }

    /**
     * What the screen draws: the last run's tabs first, then the notebooks they don't stand for.
     *
     * [offered] is the pane's claim: once any document has been opened, the session on disk has been
     * replaced (its snapshots swept), so its cards must not come back when the last tab is closed.
     */
    fun cards(offered: Boolean): List<RecentNotebook> =
        EntryCards.of(previous.value.takeIf { offered }, recent.value)
}

/** The cards to draw, or nothing of the last run once its offer is no longer valid. */
internal fun MainActivity.entryCards(): List<RecentNotebook> =
    entryRecents.cards(offered = pane.holdsPreviousSession)

/** The restore door's action, or null when there is no unclaimed session to bring back. */
internal fun MainActivity.restoreDoor(): (() -> Unit)? =
    entryRecents.previous.value?.takeIf { pane.holdsPreviousSession }?.let { { restoreLastSession() } }

/**
 * Read the session the last run left in the focused pane **without opening it**.
 *
 * Only the index is read (`TabStore.load` — no document is parsed), so this is cheap enough for the
 * launch path. It arms [com.mobixournal.panes.EditorPane.holdsPreviousSession] at the same time: until
 * the reader answers (restore, a card, *New sheet*), the empty pane must not overwrite the session it
 * is offering — an empty write would sweep every snapshot that session names away.
 */
internal fun MainActivity.readPreviousSession() {
    if (!pane.tabs.isEmpty) return
    val session = runCatching { pane.store.load() }.getOrNull() ?: return
    entryRecents.previous.value = session
    pane.holdsPreviousSession = true
}

/** Reopen a card: a notebook by its URI, a tab the last run left open by taking its session back. */
internal fun MainActivity.openEntryCard(notebook: RecentNotebook) {
    val tabId = notebook.sessionTab
    if (tabId == null) openDocument(Uri.parse(notebook.uri)) else adoptPreviousSession(tabId)
}

/** *Restore last session*: every tab the last run left open, on the tab it was left on. */
internal fun MainActivity.restoreLastSession() = adoptPreviousSession(null)

/**
 * A **recreated** window resumes instead of offering: a config change or a process-death resume is not
 * a new launch, so its tabs go straight back — the focused pane's session and, in split view, the
 * right pane's, which comes back on the canvas it builds for itself.
 */
internal fun MainActivity.reopenPreviousSession() {
    readPreviousSession()
    if (entryRecents.previous.value != null) adoptPreviousSession(null)
    if (!splitView.value) return
    val right = panes.getOrNull(1)?.takeIf { it.tabs.isEmpty } ?: return
    val session = runCatching { right.store.load() }.getOrNull() ?: return
    right.holdsPreviousSession = false
    session.tabs.forEach(right.tabs::open)
    right.tabs.select(session.activeIndex)
}

/**
 * Load the offered session into the focused pane and show [tabId], or the tab that was active.
 *
 * Nothing is written here: the pane's own next persist stamps the session back onto disk, and until
 * then the snapshots on disk are the truth (`TabStore.hydrate` parses them lazily).
 */
private fun MainActivity.adoptPreviousSession(tabId: String?) {
    val session = entryRecents.previous.value ?: return
    entryRecents.previous.value = null
    pane.holdsPreviousSession = false
    session.tabs.forEach(pane.tabs::open)
    val at = session.tabs.indexOfFirst { it.id == tabId }.takeIf { it >= 0 } ?: session.activeIndex
    pane.tabs.select(at)
    pane.tabs.active?.let { show(it, pane) }
    tabsTick.value++
}

/** *New sheet*: starting new work is the reader's answer to the offer — the last run's cards go. */
internal fun MainActivity.startFreshSession() {
    entryRecents.previous.value = null
    newTab()
}

/** A card's `×`: out of the recents, and out of the stored session when the card stands for one. */
internal fun MainActivity.removeEntryCard(notebook: RecentNotebook) {
    val tabId = notebook.sessionTab
    if (tabId == null) {
        entryRecents.forget(notebook)
        return
    }
    val session = entryRecents.previous.value ?: return
    val activeId = session.tabs.getOrNull(session.activeIndex)?.id
    val remaining = session.tabs.filterNot { it.id == tabId }
    entryRecents.previous.value = if (remaining.isEmpty()) null else TabSession(
        remaining,
        remaining.indexOfFirst { it.id == activeId }.coerceAtLeast(0),
    )
    if (remaining.isEmpty()) pane.holdsPreviousSession = false
    pane.store.dropTab(tabId)
}

/**
 * Put [uri] at the head of the entry screen's cards.
 *
 * Called before the bytes are read, so a notebook that fails to open still shows up (a card that
 * cannot be opened is more useful than one that silently vanished); its face arrives later, in
 * [rememberFaceOf].
 */
internal fun MainActivity.rememberRecentNotebook(uri: Uri, name: String) {
    entryRecents.remember(uri, name)
}

/**
 * Write [page] as [uri]'s face, on the preview worker — a page raster is milliseconds but a document
 * can be thousands of strokes, and the entry screen must never pay for it.
 */
internal fun MainActivity.rememberFaceOf(uri: Uri, page: Page) {
    val key = uri.toString()
    previewWorker.execute {
        runCatching { EntryPreviews.save(this@rememberFaceOf, key, page) }
    }
}
