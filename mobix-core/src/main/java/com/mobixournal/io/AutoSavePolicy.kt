package com.mobixournal.io

/**
 * When a document is allowed to write **itself** — the one rule both shells read.
 *
 * Auto save is off until the reader asks for it, so the first answer is always no. The rest of the rule is
 * what keeps an automatic write from doing something a person would not have done:
 *
 * - **edited** — a clean document has nothing to write, and a write with nothing in it still costs a file.
 * - **not read-only** — a document opened for looking at refuses writes by the gate's answer, and an
 *   automatic save is not an exception to a decision the reader made.
 * - **has a target** — a document that has never been saved has no file, and choosing where it lives is the
 *   reader's answer, never the app's. Auto save therefore never opens a picker: it waits for Save.
 * - **not busy** — a transfer already in flight (or a save still committing) owns the file.
 *
 * The **quiet period** is the shells': a stroke must not pay for a whole-notebook encode, so each shell waits
 * [QUIET_MS] after the last edit before asking this. The window already runs two jobs that way
 * (`DesktopQuiet.kt`); this is the third.
 */
object AutoSavePolicy {

    /** How long the edits have to be quiet before an automatic save may write the document. */
    const val QUIET_MS = 4_000L

    /** Whether a document may be written by itself, given the facts the shell holds about it. */
    fun due(
        enabled: Boolean,
        edited: Boolean,
        readOnly: Boolean,
        hasTarget: Boolean,
        busy: Boolean,
    ): Boolean = enabled && edited && !readOnly && hasTarget && !busy
}
