package com.mobixournal.panes

import com.mobixournal.format.SaveFormat
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.tabs.SnapshotSources
import com.mobixournal.tabs.TabManager
import com.mobixournal.tabs.TabStore

/**
 * One editing pane: a canvas plus the set of documents open in it.
 *
 * In split view the editor shows **two** of these side by side, and each one keeps its own tab
 * session, its own canvas (hence its own scroll/zoom and undo history) and its own sticky save
 * format. Whichever pane the user last touched is the *active* one; every menu and toolbar action
 * in [com.mobixournal.MainActivity] is routed to it, which is what lets the rest of the app carry
 * on being written against "the" document.
 *
 * Panes are persisted independently — each has its own [TabStore] directory — so a split session
 * comes back the way it was left.
 */
class EditorPane(
    /** Where this pane's open tabs (unsaved edits included) are cached across restarts. */
    val store: TabStore,
    /**
     * What the live canvas can hand a snapshot for the document on it ([SnapshotSources]) — the app
     * wires this because the answer comes from two layers this pane does not know: the document I/O
     * that owns a board's shape, and the canvas that holds the imported sources.
     */
    private val sourcesOf: (DrawingSurfaceView) -> SnapshotSources = { SnapshotSources() },
) {
    /** The live canvas, once Compose has created it. Null before the first composition. */
    var surface: DrawingSurfaceView? = null

    /** The documents open in this pane and which one is showing. */
    val tabs = TabManager()

    /**
     * The sticky save format for this pane. "Save As" sets it; every later plain Save reuses it.
     * Opening a document adopts the format it was stored in.
     */
    var saveFormat: SaveFormat = SaveFormat.ORIGINAL

    /** The file name last chosen in the Save As dialog for this pane; reused by plain Save. */
    var pendingSaveName: String = "document.xopp"

    /**
     * Write this pane's session out — called on every tab change and on the way to the background.
     *
     * The session is read here, on the caller's (main) thread, so it is a consistent snapshot; the
     * writing itself is gzip XML per open document, far too slow for the main thread, so it is queued
     * onto [writer]. One single-threaded queue keeps the writes in the order they were asked for, so
     * the last state asked for is the state left on disk.
     */
    fun persist() {
        val session = tabs.session()
        // The app's stores hold this document's sources only while it is on the canvas, so they are read
        // here, with the session: a `.mobix` snapshot has to carry a picture or a recording that was
        // imported this session ([SnapshotSources]), and this is the moment it can be asked.
        val sources = liveSources()
        writer.execute { store.save(session, sources) }
    }

    /** What the canvas can supply for the active tab's document — nothing before it exists. */
    private fun liveSources(): SnapshotSources = surface?.let(sourcesOf) ?: SnapshotSources()

    /**
     * Block until the queued session writes have finished — used when the app is going away and the
     * snapshot has to be on disk before the process can be killed.
     */
    fun awaitPersist(timeoutMs: Long) {
        val done = java.util.concurrent.CountDownLatch(1)
        writer.execute(done::countDown)
        runCatching { done.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }
    }

    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
}
