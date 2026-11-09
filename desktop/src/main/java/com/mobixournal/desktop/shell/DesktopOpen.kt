package com.mobixournal.desktop.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.desktop.io.DesktopAudioSession
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.io.DesktopRecovery
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.io.describeOpenFailure
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The window's opens, off the frame thread.
 *
 * A read used to run on the thread the frame loop paints on, so opening a notebook stopped every paint for
 * the length of the read: `--diagnose` recorded gaps of 1 032-1 861 ms around an open whose paint was 1 ms,
 * and a click made inside one of those gaps did nothing until it ended. The window's own reading of that is
 * the felt delay between tabs (`docs/desktop-ui-layout.md` owns the band's sentence).
 *
 * Apart from [DesktopShellState] rather than inside it: the state's file is at its recorded ceiling, and this
 * is a separate job — the read, not the tab bookkeeping.
 */
internal object DesktopOpens {

    /**
     * The file an open is reading, by its name, or `null`.
     *
     * With nothing on screen the band reads `no document`, which stays true and useless for the length of a
     * read; it names what it is waiting for instead ([StatusBar]).
     */
    var opening: String? by mutableStateOf(null)

    /**
     * Bumped when a document lands, so the entry screen re-reads the recent list it is drawing.
     *
     * The list itself lives in the settings file, which is not Compose state; the screen keys its `remember`
     * on this counter instead of re-reading a properties file on every frame.
     */
    var recentsRevision: Int by mutableStateOf(0)
}

/**
 * [DesktopShellState.open] with its read **off** the caller's thread.
 *
 * The read runs on [Dispatchers.IO] and the tab is built back where the call came from, so Compose state is
 * written on the thread that composes it. A PDF is handed to [DesktopShellState.open], which puts its own
 * dialog: the question there is the reader's, not a read to move.
 *
 * [load] is a parameter because the claim is the *return* — that the call comes back before the read — and
 * only a read that can be held open can witness it (`DesktopOpenBackgroundTest`).
 */
internal fun DesktopShellState.openInBackground(
    file: File,
    scope: CoroutineScope,
    load: (File) -> DesktopDocument = DocumentLoader::load,
) {
    if (DocumentLoader.isPdf(file)) { open(file); return }
    // A snapshot is newer than the file it stands for: a notebook the last run left dirty must land on
    // its recovered edits, never on a second view of the older bytes beside work that is not lost yet.
    recovery?.entryFor(file)?.let { pending ->
        restoreInBackground(pending, scope)
        return
    }
    DesktopOpens.opening = file.name
    scope.launch {
        val loaded = withContext(Dispatchers.IO) { runCatching { load(file) } }
        DesktopOpens.opening = null
        loaded.onSuccess { document -> opened(document, file) }.onFailure { failure = describeOpenFailure(it) }
    }
}

/**
 * A **previous-session** document, read off the frame thread — the badged card's door on the entry
 * screen. The tab is built back on the composing thread, like every other open.
 */
internal fun DesktopShellState.restoreInBackground(entry: DesktopRecovery.Entry, scope: CoroutineScope) {
    DesktopOpens.opening = entry.title
    scope.launch {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { DocumentLoader.loadRecovered(entry.snapshot, entry.origin) }
        }
        DesktopOpens.opening = null
        loaded.onSuccess { document -> adoptRecovered(document, entry) }
            .onFailure { failure = describeOpenFailure(it) }
    }
}

/** The bookkeeping an open finishes with, wherever its read ran. */
internal fun DesktopShellState.adoptLoaded(document: DesktopDocument) {
    DesktopAudioSession.adopt(document)
    tabs.open(document, prefs.fitOnOpen)
    failure = null
}

/**
 * A document that landed, from its own file: the tab, and the entry screen's memory of it.
 *
 * The recent list is written **here** rather than where the picker was answered, because a file that failed
 * to open is not a notebook the reader can be offered again.
 */
internal fun DesktopShellState.opened(document: DesktopDocument, file: File) {
    adoptLoaded(document)
    settings.recents.record(file)
    DesktopOpens.recentsRevision++
}
