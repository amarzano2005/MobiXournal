package com.mobixournal.desktop

/**
 * The window's **close path**: the one road from "the user wants this gone" to a tab that is closed,
 * written once so the strip's close glyph, `Ctrl+W`, the window's X and the confirm dialog cannot take
 * different roads.
 *
 * It lives beside `DesktopShell.kt` rather than inside it because the state class is already the window's
 * whole command surface and the module keeps files under 700 lines (`SourceStructureTest`). These are
 * extension functions, so every call site still says `state.requestCloseTab(index)`.
 *
 * Two rules run through all of it:
 *  - **nothing is lost without being asked** — a dirty tab becomes a [CloseRequest], never a close, and
 *    a failure to save leaves the tab open and dirty rather than closing on top of the loss;
 *  - **the answers are the shell's** — Save writes the tab's own file, Discard drops the crash snapshot
 *    with the edits, Cancel leaves everything exactly as it was.
 *
 * The request is about one tab at a time even when the window is closing: answering it moves to the next
 * dirty tab (the dialog stays up and its title changes), and the window goes only when there is none left.
 */

/**
 * A close the window is asking about: the tab with edits the file does not hold, and whether the request
 * came from that tab's close glyph (`exit = false`) or from the window's X (`exit = true`, the session).
 */
class CloseRequest internal constructor(val tab: DesktopTab, val exit: Boolean)

/**
 * Ask to close tab [index]. A **clean** tab closes at once (its file already holds it) and answers true;
 * a dirty one answers false and puts the question on screen. The answer is "did it close", which is what
 * `Ctrl+W` and the close glyph need to know.
 */
fun DesktopShellState.requestCloseTab(index: Int): Boolean {
    if (closeRequest != null) return false
    val tab = tabs.all.getOrNull(index) ?: return false
    if (tab.isDirty) {
        closeRequest = CloseRequest(tab, exit = false)
        return false
    }
    settle(tab)
    return true
}

/** `Ctrl+W`: the active tab, through the same question. */
fun DesktopShellState.requestCloseActive(): Boolean = requestCloseTab(tabs.activeIndex)

/**
 * Ask to close the **window** — what its X button calls.
 *
 * Answers true when it may go now (nothing dirty), false when a question is up. It activates the tab it
 * asks about: a question about a document the user cannot see is a question about the wrong document.
 */
fun DesktopShellState.requestExit(): Boolean {
    if (exitAllowed) return true
    if (closeRequest != null) return false
    val dirty = tabs.all.firstOrNull { it.isDirty }
    if (dirty == null) {
        exitAllowed = true
        return true
    }
    tabs.activate(dirty)
    closeRequest = CloseRequest(dirty, exit = true)
    return false
}

/**
 * **Save**: write the tab's own file, then let the close continue.
 *
 * A failed save closes the question but not the tab: the reason is on screen ([failure]) and the edits
 * are still in the document, which is the only outcome that cannot lose work.
 */
fun DesktopShellState.confirmCloseSave() {
    val request = closeRequest ?: return
    val tab = request.tab
    val saved = runCatching { tab.saveBack() }
        .onFailure { failure = describeSaveFailure(it) }
        .isSuccess
    closeRequest = null
    if (!saved) return
    failure = null
    settle(tab)
    continueClose(request.exit)
}

/** **Discard**: drop the edits and the crash snapshot with them, then let the close continue. */
fun DesktopShellState.confirmCloseDiscard() {
    val request = closeRequest ?: return
    closeRequest = null
    settle(request.tab)
    continueClose(request.exit)
}

/** **Cancel**: leave the document, its edits and the snapshot exactly as they were. */
fun DesktopShellState.cancelClose() {
    closeRequest = null
    exitAllowed = false
}

/**
 * Adopt every crash snapshot a previous run left behind as a **recovered** tab, and answer how many were
 * adopted. It runs at start, before the document named on the command line is opened, so that file's tab
 * is the recovered one — the edits are newer than the file, and activating a second view of it would show
 * the older bytes.
 */
fun DesktopShellState.recover(): Int {
    val store = recovery ?: return 0
    var adopted = 0
    for (entry in store.entries()) {
        val document = runCatching { DocumentLoader.loadRecovered(entry.snapshot, entry.origin) }
            .getOrNull() ?: continue
        val tab = DesktopTab(
            document,
            title = "${entry.title} (recovered)",
            recoveredFrom = entry.snapshot,
        )
        if (tabs.adopt(tab) === tab) adopted++
    }
    return adopted
}

/** A committed edit: restarts the shell's quiet period for the crash snapshot. */
fun DesktopShellState.noteEdit() {
    editRevision++
}

/**
 * Write a snapshot of every dirty tab, and answer how many were written.
 *
 * Called after a burst of edits has gone quiet (`DesktopShell` debounces it), and **not** here: a
 * snapshot is the whole notebook, so the cadence is a decision the window makes, not this method.
 */
fun DesktopShellState.flushRecovery(): Int {
    val store = recovery ?: return 0
    var written = 0
    for (tab in tabs.all) {
        if (tab.isDirty && store.save(tab) != null) written++
    }
    return written
}

/** The edits are in the file: the crash snapshot has no reason to outlive them. */
internal fun DesktopShellState.noteSaved(tab: DesktopTab) {
    recovery?.forget(tab.file)
    tab.forgetRecovery()
}

/**
 * Settle [tab]'s crash snapshot — delete the file and forget it — then close the tab if it is still
 * open. A snapshot outliving the edits it holds is the one way a recovered tab can be offered twice or
 * resurrect work the user discarded, so every close path comes through here.
 */
internal fun DesktopShellState.settle(tab: DesktopTab) {
    noteSaved(tab)
    val index = tabs.all.indexOf(tab)
    if (index >= 0) tabs.close(index)
}

/** After a closed tab: with the window closing, ask about the next dirty one, or let it go. */
private fun DesktopShellState.continueClose(exit: Boolean) {
    if (!exit) return
    val next = tabs.all.firstOrNull { it.isDirty }
    if (next == null) {
        exitAllowed = true
        return
    }
    tabs.activate(next)
    closeRequest = CloseRequest(next, exit = true)
}
