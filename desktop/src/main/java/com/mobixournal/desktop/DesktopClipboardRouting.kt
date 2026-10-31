package com.mobixournal.desktop

/**
 * **Which clipboard a clipboard key means** — the one decision behind `Ctrl+X`, `Ctrl+C`, `Ctrl+V` and
 * `Ctrl+D`.
 *
 * The window now has two clipboards: the elements a marquee caught (`DesktopClipboard`) and the pages the
 * sidebar's pick names (`DesktopPageClipboard`). A key cannot ask, so the rule is the state the reader is
 * in, in one order:
 *
 *  1. a **PDF text selection** owns Copy while it is up: what is copied is words, and copying words is a
 *     reading — no edit, no undo step (`docs/pdf.md`);
 *  2. a **page pick with no marquee** means pages: the reader pointed at rows, so that is what a Copy,
 *     Cut or Paste is about. `Ctrl+D` follows the same logic one level up — no ink selected is the page in
 *     view;
 *  3. everything else is the tablet's element clipboard, exactly as before, so no gesture that worked
 *     changes meaning.
 *
 * Kept out of `DesktopShell.kt` because the shell is about the window: this file is about two clipboards
 * and the order they are consulted in, and it is the only place that order is written.
 */
internal fun DesktopShellState.clipboardCommand(
    tab: DesktopTab,
    command: ShellCommand,
    edited: (Int) -> Unit,
): Boolean = when (command) {
    ShellCommand.CUT_SELECTION ->
        if (tab.selection == null && cutPickedPages()) true
        else cutSelection(tab, clipboard, edited)
    ShellCommand.COPY_SELECTION ->
        if (copyPdfTextOf(tab)) true
        else if (tab.selection == null && copyPickedPages()) true
        else copySelection(tab, clipboard)
    ShellCommand.PASTE_CLIPBOARD ->
        if (tab.selection == null && pastePages()) true
        else pasteClipboard(tab, clipboard, onEdited = edited)
    ShellCommand.DUPLICATE_SELECTION ->
        if (tab.selection == null && duplicatePage(tab.page)) true
        else duplicateSelection(tab, edited)
    else -> false
}
