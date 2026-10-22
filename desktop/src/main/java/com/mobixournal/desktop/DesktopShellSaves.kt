package com.mobixournal.desktop

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
        .onSuccess { failure = null }
        .getOrNull()
}

/**
 * Write the active document to [file]. The container follows the name the user gave it
 * ([DesktopSaves.formatFor]): `.mobix` writes the native archive, a `.xopp` the gzip or packaged form,
 * an unrecognised name keeps the document's own — which is the one place the sticky format changes
 * (`docs/data-path.md`), so the tab then remembers the new file and container and the next Save writes
 * back there.
 */
fun DesktopShellState.saveAs(file: File): List<Uncompilable>? {
    val tab = tabs.active ?: return null
    val format = DesktopSaves.formatFor(file.name, tab.document.container)
    return runCatching { DesktopSaves.save(tab.document, file, format) }
        .onFailure { failure = describeSaveFailure(it) }
        .onSuccess {
            failure = null
            tab.rehome(file)
        }
        .getOrNull()
}
