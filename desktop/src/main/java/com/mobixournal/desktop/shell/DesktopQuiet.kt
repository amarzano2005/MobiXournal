package com.mobixournal.desktop.shell
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopRecovery
import com.mobixournal.desktop.io.save
import com.mobixournal.io.AutoSavePolicy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The two jobs the shell runs **after a burst of edits goes quiet** — and why each of them waits.
 *
 * Both are things a stroke must not pay for. A crash snapshot is the whole notebook
 * ([DesktopShellState.flushRecovery]), and a page thumbnail is a page painted *and* rasterised in software
 * ([renderPageBitmap] — 29–39 ms on `udiff.xopp`, measured). Running either per stroke is what made writing
 * stutter: the snapshot traded one risk for another, and the thumbnail, rendered inside composition, dropped
 * four frames on every finished letter and again on every bite of the eraser.
 *
 * A cancelled coroutine **is** the debounce: the next edit cancels the job the previous one scheduled, and
 * the shell's state bump ([DesktopShellState.editRevision]) is the only thing that starts them. Two
 * different quiet periods, because the two costs are an order of magnitude apart and the preview should
 * catch up as soon as the hand pauses.
 */

/** How quiet the edits have to be before a crash snapshot is written: a snapshot is the whole notebook. */
private const val RECOVERY_QUIET_MS = 3_000L

/**
 * How quiet the edits have to be before a page thumbnail is painted again.
 *
 * Short enough that the preview catches up as soon as a word is finished, long enough that a stroke's own
 * lift never falls inside it.
 */
private const val THUMBNAIL_QUIET_MS = 600L

/**
 * The crash snapshot, written once the edits have been quiet for a moment.
 *
 * Short enough that a crash loses seconds, long enough that a continuous stroke never writes one. The
 * write itself is a whole notebook encoded and gzipped, so it runs on [Dispatchers.IO]: on the UI thread it
 * was a freeze in the middle of a pause between two letters, which is a hitch a note-taker feels and cannot
 * explain. A write that loses a race with a new stroke fails quietly (`DesktopRecovery.save` answers null)
 * and the next quiet moment tries again.
 */
@Composable
internal fun RecoverySnapshots(state: DesktopShellState) {
    LaunchedEffect(state.editRevision) {
        if (state.editRevision == 0) return@LaunchedEffect
        delay(RECOVERY_QUIET_MS)
        val writes = state.prepareRecovery()
        // The **save lane**, not a pool of its own: a snapshot streams the whole notebook under the document's
        // read lock, and a save's commit needs the write lock — on separate lanes they spent their time waiting
        // for each other, and the file was still unwritten while the quiet timer re-armed the snapshot.
        withContext(SAVE_IO) { writes.forEach { (store, request) -> store.save(request) } }
    }
}

/**
 * The page thumbnails, re-rendered once the edits have been quiet for a moment.
 *
 * The sidebar keys its rows on [DesktopShellState.thumbnailRevision], this bumps it after the burst, and
 * until then the sidebar shows the page as it was when the burst began — a preview one stroke stale instead
 * of a window that stutters per stroke.
 */
@Composable
internal fun ThumbnailRefresh(state: DesktopShellState) {
    LaunchedEffect(state.editRevision) {
        if (state.editRevision == 0) return@LaunchedEffect
        delay(THUMBNAIL_QUIET_MS)
        state.refreshThumbnails()
    }
}

/**
 * **Auto save**: with the setting on, a document that has a file of its own is written once the edits have been
 * quiet, so a stroke never pays for the encode.
 *
 * It is a quiet job rather than a per-stroke one for the same reason the crash snapshot is: the write stages a
 * whole notebook. Keyed on [DesktopShellState.editRevision] the way the other two are, so the next edit cancels
 * the pending save and restarts the wait — which is what "after you stop writing" means. [autoSaveNow] re-reads
 * every gate when it runs, so a document that became read-only, lost its file or was cleaned in the meantime is
 * simply not written.
 */
@Composable
internal fun AutoSave(state: DesktopShellState) {
    LaunchedEffect(state.editRevision) {
        if (state.editRevision == 0) return@LaunchedEffect
        delay(AutoSavePolicy.QUIET_MS)
        state.autoSaveNow()
    }
}

/**
 * Write the showing document by itself, if the window is allowed to.
 *
 * The same path `Ctrl+S` takes on a tab that already has a file (`save`), never the picker: a document with no
 * name of its own waits for the reader. Every answer is [AutoSavePolicy]'s, so the two shells agree on what an
 * automatic save may touch.
 */
internal fun DesktopShellState.autoSaveNow() {
    val tab = tabs.active ?: return
    val due = AutoSavePolicy.due(
        enabled = prefs.autoSave,
        edited = tab.isDirty,
        readOnly = tab.document.readOnly,
        hasTarget = !tab.unsaved,
        // A save already staging is busy too: the answer used to be the commit alone, so a quiet moment that
        // landed inside a write staged the same notebook a second time.
        busy = saveState.request != null || saveState.committing,
    )
    if (!due) return
    // **Through the worker**, when the window is up: `save()` encodes the whole notebook and writes it on the
    // calling thread, and this one is the thread that paints — so auto save ran a full encode and a write
    // inline, which is the freeze a reader sees as "it hangs while it saves", on exactly the documents where
    // the encode is longest. The synchronous path stays for a shell with no composition to hand the request
    // to (the JVM tests), the same choice `saveOrPick` makes for the header's Save.
    if (saveState.uiBound) requestSave() else save()
}
