package com.mobixournal.desktop.shell

import com.mobixournal.desktop.io.DesktopRecovery
import java.io.File
import kotlinx.coroutines.CoroutineScope

/**
 * What the entry screen draws, and what its cards do — the window's answer to the tablet's recents.
 *
 * Two kinds of card, one grid. A **recent** is a notebook opened lately, from `recent.files`. A
 * **previous-session** card is a document the last run left with edits that never reached its file:
 * the window keeps no tab list on disk, so that card is exactly a crash snapshot in `recovery/`
 * (`DesktopRecovery`), drawn with the restore badge and labelled by the tab it stands for.
 *
 * The previous run's documents come **first**, because they carry the newest work there is, and a
 * recent the same snapshot already stands for is left out — two cards for one document would ask the
 * reader which copy they meant. Nothing here opens a document: a card is read, and a click decides
 * between restoring a snapshot and opening a file.
 *
 * Beside `DesktopShell.kt` rather than inside it because the state class is the window's whole command
 * surface and is at its recorded ceiling (`SourceStructureTest`).
 */
internal class EntryCard(
    /** The card's identity: the notebook's path, or the snapshot's — stable across frames. */
    val id: String,
    /** The file the card opens, and the file a recovered tab would save back to. */
    val file: File,
    /** The caption: the file's name, unless the snapshot remembers a tab that was renamed. */
    val title: String,
    /** The snapshot this card would restore, or null for a notebook opened lately. */
    val recovery: DesktopRecovery.Entry? = null,
) {
    /** True when the card stands for work the last run never wrote to its file. */
    val fromPreviousSession: Boolean get() = recovery != null
}

/** The snapshots the previous run left, oldest first — nothing has adopted them yet. */
internal fun DesktopShellState.previousSession(): List<DesktopRecovery.Entry> =
    recovery?.entries().orEmpty()

/** The entry screen's grid: the previous run's documents first, then the recents they do not claim. */
internal fun DesktopShellState.entryCards(): List<EntryCard> {
    val pending = previousSession()
    val claimed = pending.map { DesktopTabs.keyOf(it.origin) }.toSet()
    return pending.map { entry ->
        EntryCard("recovery:${entry.snapshot.absolutePath}", entry.origin, entry.title, recovery = entry)
    } + settings.recents.files
        .filterNot { DesktopTabs.keyOf(it) in claimed }
        .map { file -> EntryCard("recent:${file.absolutePath}", file, file.nameWithoutExtension) }
}

/**
 * A card's **×**: the notebook leaves the entry screen.
 *
 * A recent is only forgotten — the file itself is untouched, and opening it again puts it back in
 * front. A previous-session card also drops its snapshot, because a card taken off the screen and a
 * snapshot left on disk would offer the same work again at the next start.
 */
internal fun DesktopShellState.forgetEntry(card: EntryCard) {
    val pending = card.recovery
    if (pending != null) recovery?.forget(pending.origin) else settings.recents.forget(card.file)
    DesktopOpens.recentsRevision++
}

/** A card's door: the recovered tab for a previous-session document, the file itself for a recent. */
internal fun DesktopShellState.openEntry(card: EntryCard, scope: CoroutineScope) {
    val pending = card.recovery
    if (pending != null) restoreInBackground(pending, scope) else openInBackground(card.file, scope)
}

/** *Restore last session*: adopt every snapshot the previous run left, answering how many came back. */
internal fun DesktopShellState.restoreLastSession(): Int {
    val restored = recover()
    DesktopOpens.recentsRevision++
    return restored
}

/**
 * *New sheet* from the entry screen: a blank page, and the offer **dropped** with it — the same
 * answer the tablet gives, so a reader who chose a fresh sheet is not asked about the old one again
 * at the next start.
 */
internal fun DesktopShellState.startFreshSession() {
    previousSession().forEach { entry -> recovery?.forget(entry.origin) }
    DesktopOpens.recentsRevision++
    newDocument()
}
