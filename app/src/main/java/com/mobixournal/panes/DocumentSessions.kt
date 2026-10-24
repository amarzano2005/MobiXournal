package com.mobixournal.panes

import com.mobixournal.format.model.Document
import com.mobixournal.render.EditHistory

/**
 * The **sessions** behind the views of a document: one undo history per document, keyed by the same
 * `OpenTab.docKey` that says two tabs are views of one file.
 *
 * Split view and OS multi-window make a document openable in more than one view at once, and undo is
 * a fact of the *document*, not of a pane: undoing a stroke in the left view must take it off the
 * right one too, and the next redo must be the one that stroke. A history owned by each canvas cannot
 * do that — an edit arriving through the mirror would leave the other canvas's snapshots describing a
 * document that no longer exists, which is why that path used to clear it instead. Handing every view
 * of one key the *same* [EditHistory] is the smallest honest fix: one stack, one state id, one answer
 * to "can I undo".
 *
 * Plain Kotlin, no Android or Compose types, so the sharing rule is pinned by a JVM test. The owner
 * of the panes ([MirrorSync]) decides when a document is closed and calls [forget].
 */
class DocumentSessions {

    private val histories = HashMap<String, EditHistory<Document>>()

    /**
     * The one history for [key], created by [create] on first use. Two views of one document call this
     * with the same key and receive the **identical** instance, which is what makes an undo on either
     * side an undo of the document.
     */
    fun history(key: String, create: () -> EditHistory<Document> = { EditHistory() }): EditHistory<Document> =
        histories.getOrPut(key, create)

    /** The history already held for [key], or null when no view of it has asked yet. */
    fun existing(key: String): EditHistory<Document>? = histories[key]

    /** Whether [key] has a session (a view asked for its history, or the owner registered one). */
    fun has(key: String): Boolean = key in histories

    /** Drop the session for [key] — the document was closed, or its last view went away. */
    fun forget(key: String) {
        histories.remove(key)
    }

    /** Drop every session (a wholesale document/tab teardown). */
    fun clear() {
        histories.clear()
    }

    /** The keys with a live session, for diagnostics and tests. */
    fun keys(): Set<String> = histories.keys.toSet()

    companion object {
        /**
         * The process-wide sessions. An Activity (or a freeform window of this app) that mirrors a
         * document registers the same key here, so its undo is the same stack — the state lives above
         * any one window, which is what "one history per document" means once more than one window can
         * show it.
         */
        val shared = DocumentSessions()
    }
}
