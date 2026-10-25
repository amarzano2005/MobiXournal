package com.mobixournal.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
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
        withContext(Dispatchers.IO) { state.flushRecovery() }
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
