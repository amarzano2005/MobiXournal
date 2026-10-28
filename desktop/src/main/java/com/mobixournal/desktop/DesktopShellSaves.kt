package com.mobixournal.desktop

import com.mobixournal.format.SaveFormat
import com.mobixournal.render.Uncompilable
import java.io.File

/**
 * The shell's two **save** commands, beside the state they act on rather than inside it.
 *
 * [DesktopShellState] is the window's whole command surface in one class, and `DesktopShell.kt` is kept
 * under the module's 700-line ratchet (`SourceStructureTest`); the save pair is a self-contained pair of
 * decisions — which file, which container — so it reads better here than buried in the middle of the
 * key handling. These are extension functions, so every call site still says `state.save()`, and the
 * state they touch is the shell's public surface (`tabs`, `failure`).
 *
 * What they must not do is duplicate the write: the bytes are [DesktopSaves] (the tab's own path for a
 * save-back, which re-opens the file after the move), and this file only chooses the destination and
 * turns a failure into the sentence the window shows.
 */

/**
 * Write the active document back to its own file, in the container it was opened as
 * ([DesktopSaves.formatOf]), through the tab so the document is re-opened onto the file it wrote.
 *
 * Answers what the write could not draw — an entity this build has no `.xopp` drawing for is a page
 * short, not a detail — or null when the save failed (the reason then sits in [DesktopShellState.failure]).
 */
fun DesktopShellState.save(): List<Uncompilable>? {
    val tab = tabs.active ?: return null
    return runCatching { tab.saveBack() }
        .onFailure { failure = describeSaveFailure(it) }
        .onSuccess {
            failure = null
            // The file holds the edits now, so the crash snapshot of the old ones goes.
            noteSaved(tab)
        }
        .getOrNull()
}

/**
 * Write the active document to [file], as [format] — the type the Save As picker's box answered.
 *
 * The format is the **window's answer** (`SaveAsPicker`), not a guess from the name: a name without a
 * suffix — or one typed over it — would otherwise silently keep the document's container and make the
 * choice the reader just made a no-op. Null keeps the old behaviour for a caller that has no answer to
 * give ([DesktopSaves.formatFor] reads the name).
 *
 * Save As is the one place the sticky format changes (`docs/data-path.md`): the tab then remembers the new
 * file and container and the next Save writes back there.
 */
fun DesktopShellState.saveAs(file: File, format: SaveFormat? = null): List<Uncompilable>? {
    val tab = tabs.active ?: return null
    val chosen = format ?: DesktopSaves.formatFor(file.name, tab.document.container)
    return runCatching { DesktopSaves.save(tab.document, file, chosen) }
        .onFailure { failure = describeSaveFailure(it) }
        .onSuccess {
            failure = null
            // Settled before the tab moves: the snapshot is keyed by the file it stood in for.
            noteSaved(tab)
            tab.rehome(file)
        }
        .getOrNull()
}
