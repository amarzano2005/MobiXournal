package com.mobixournal.desktop

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.window.FrameWindowScope
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * The desktop window's **window side**: the system file dialog, the focus the keys arrive on, and the
 * shell itself.
 *
 * Everything that can be tested without a window lives in [DesktopShellState] and [DesktopShell]; what
 * is left here is the part that genuinely needs one — `FrameWindowScope` for a native [FileDialog],
 * and a focus requester so `Ctrl+T` reaches the app at all.
 *
 * Every word the window shows is **English**, like the tablet app's UI (`docs/ui-modernization.md`
 * owns the rule): the desktop is the same product, not a translated companion.
 *
 * The keys are in [DesktopShellState.onKey] and the shortcuts table of `docs/ui-modernization.md`; the
 * colours are [DesktopTheme], which reads the shared [com.mobixournal.design.DesignTokens] — the window
 * carries no palette of its own.
 */
@Composable
fun FrameWindowScope.DesktopApp(
    initial: File? = null,
    state: DesktopShellState = remember { DesktopShellState(recovery = DesktopRecovery()) },
    /** What the window does once every dirty tab has been answered — `exitApplication`, in `Main.kt`. */
    onExit: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val dialogs = remember { DocumentDialogs(window, state) }
    // Whether anything in the window holds the keyboard. A panel that closes takes its text field with
    // it, and Compose is then left with **no** focused element — the shortcuts would be dead until the
    // page was clicked. The window hands the keyboard back to its root instead, so `Ctrl+Z` or `G` works
    // the moment the panel is gone.
    var keyboardHeld by remember { mutableStateOf(false) }

    // Recovery first: a snapshot is newer than the file it stands for, so a file named on the command
    // line then lands on its recovered tab instead of opening a second view of the older bytes.
    LaunchedEffect(initial) {
        // Recovered edits first, then the document named on the command line; with neither, a **new
        // page**, because the window's default screen is a sheet to write on, not an empty notice.
        state.start(initial)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(keyboardHeld) { if (!keyboardHeld) runCatching { focus.requestFocus() } }
    // The system's title bar cannot be translucent, so it is painted the band's composited colour:
    // one surface from the top of the window down (`DesktopTitleBar`).
    LaunchedEffect(Unit) { applyDarkTitleBar(window) }
    // `F11` and the Display row only set a flag: taking the whole screen, and asking the panel for the
    // target rate, is the window's own business (`DesktopDisplay`). Keyed on the rate as well, so picking
    // another target while full screen asks the display again without leaving and re-entering the mode.
    val fullScreen = state.prefs.fullScreen
    val targetHz = state.prefs.targetHz
    LaunchedEffect(fullScreen, targetHz) {
        if (fullScreen) FullScreenMode.enter(window, targetHz) else FullScreenMode.leave(window)
    }
    // A window closed while on the whole screen puts the display back: leaving a panel at 144 Hz for the
    // rest of the session is a change the reader never asked the desktop for.
    DisposableEffect(Unit) { onDispose { FullScreenMode.leave(window) } }
    // The window's X asked the shell first (`Main`), and the shell says when every answer is in.
    LaunchedEffect(state.exitAllowed) { if (state.exitAllowed) onExit() }
    // A tab holds a reader's file handle (a `.mobix` archive); closing the window closes them all.
    DisposableEffect(Unit) { onDispose { state.tabs.closeAll() } }

    // The pen and the dial, opened after the window exists and closed with it ([rememberTablet]).
    val tablet = rememberTablet(window, state)
    // `Ctrl+E` arrives at the shell, which has no window to hang a dialog on: the window lends it one for
    // as long as it is up.
    DisposableEffect(Unit) {
        DesktopExportQueue.pickPdfDestination = dialogs::exportPdf
        onDispose { DesktopExportQueue.pickPdfDestination = null }
    }
    // The Backup section's two rows need the same thing the PDF export does: a window to hang a native
    // dialog on. The state has none, so the window lends it one for as long as it is up.
    DisposableEffect(Unit) {
        state.prefs.pickBackupFile = dialogs::backup
        onDispose { state.prefs.pickBackupFile = null }
    }
    DesktopTheme.Apply {
        DesktopShell(
            state = state,
            onOpenPicker = dialogs::openDocument,
            // Save As is **one** dialog that answers both of its questions (`SaveAsPicker`): the name of
            // the file, and the container in its type box.
            onSavePicker = dialogs::saveAs,
            onImportPdfPicker = dialogs::importPdf,
            onExportPdfPicker = dialogs::exportPdf,
            tablet = tablet,
            modifier = Modifier
                .focusRequester(focus)
                .focusable()
                .onFocusChanged { keyboardHeld = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    state.onKey(event, dialogs::openDocument, dialogs::saveAs, dialogs::exportPdf)
                },
        )
    }
}

/**
 * The window's **system file dialogs**, as one object: open, Save As, Import PDF, Export PDF and the
 * settings backup's file. Each needs a `Frame` to hang a native `FileDialog` on — the one thing a composable cannot be tested
 * with — so they live beside [DesktopApp] rather than inside it, where every line counts against the
 * module's function ratchet (`SourceStructureTest`).
 *
 * [saveAs] asks both of a save's questions at once: the picker's type box chooses the container and its name
 * field the file (`SaveAsPicker`).
 */
internal class DocumentDialogs(private val window: Frame, private val state: DesktopShellState) {

    /** The system picker for a document to open. */
    fun openDocument() {
        chooseDocument(window)?.let(state::open)
    }

    /**
     * Export PDF: the dialog proposes the document's own name with a `.pdf` suffix, so the export lands
     * beside the notebook unless the user says otherwise. The write itself is queued ([DesktopExportQueue])
     * and runs off the UI thread.
     */
    fun exportPdf() {
        val tab = state.active ?: return
        val proposed = "${tab.file.nameWithoutExtension}.pdf"
        chooseSave(window, "Export the document as a PDF", proposed)
            ?.let { DesktopExportQueue.request(ExportRequest.Pdf(it)) }
    }

    /**
     * Save As: one dialog names the file **and** carries the container in its type box, and the write uses
     * the container that box answers **whatever the name field ends up saying** — typing over the suffix is
     * ordinary, and reading the name instead of the type is what used to undo the choice in silence.
     */
    fun saveAs() {
        val tab = state.active ?: return
        val (file, format) = SaveAsPicker.choose(window, tab) ?: return
        state.saveAs(file, DesktopSaves.formatOf(format.container))
    }

    /**
     * Import PDF: the picker names a `.pdf` and the question that follows asks where its pages go
     * (`ImportPdfDialog`). The filter is a convenience on Windows, where AWT ignores it — the PDF's own
     * bytes are what decide whether it can be read.
     */
    fun importPdf() {
        val chosen = choosePdf(window) ?: return
        state.askImportPdf(chosen)
    }

    /** The settings backup's own file: **save** asks where to write one, **load** which to read. */
    fun backup(save: Boolean, onPicked: (File?) -> Unit) {
        onPicked(chooseBackupFile(window, save))
    }
}

/**
 * The pen and the dial, opened once per window and reopened when a setting changes the device.
 *
 * Both readers answer `null` on a host without them, which the status bar then says in words, and the dial's
 * events arrive on Windows' own thread, so they are posted to the window's event queue before they touch any
 * state. The effect is **keyed on the pen-stream preference and on the active area**: either choice reopens
 * the readers with the next frame, which is the one hook that cannot be a value the canvas reads — a stream,
 * and the rectangle it maps, are a device opened once.
 */
@Composable
private fun rememberTablet(window: Frame, state: DesktopShellState): DesktopTablet {
    val tablet = remember { DesktopTablet() }
    val penStream = state.prefs.penStream
    val windowArea = state.prefs.windowArea
    DisposableEffect(penStream, windowArea) {
        tablet.open(
            window = window,
            onRotation = { degrees -> EventQueue.invokeLater { state.dialZoom(degrees) } },
            onClick = { EventQueue.invokeLater { state.dialFit() } },
            log = { println("MobiXournal tablet: $it") },
            prefer = penStream,
            area = windowArea,
        )
        onDispose { tablet.close() }
    }
    return tablet
}

/**
 * The native open dialog, or null when it was cancelled.
 *
 * `FileDialog`'s filter is ignored on Windows, so the `.xopp`/`.mobix` suffix test is a convenience for the
 * platforms that honour it and never a restriction: the loader identifies a document by its bytes.
 */
private fun chooseDocument(window: Frame): File? {
    val dialog = FileDialog(window, "Open a document", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.endsWith(".xopp", ignoreCase = true) || name.endsWith(".mobix", ignoreCase = true)
    }
    return awaitChoice(dialog)
}

/**
 * The settings backup's own file dialog: **save** asks where to write one, **load** which to read.
 *
 * The proposed name carries the backup suffix, and a name typed without one is given it: a file whose
 * name does not say what it is would be the one thing the import's own stamp test then has to explain.
 */
private fun chooseBackupFile(window: Frame, save: Boolean): File? {
    val dialog = FileDialog(
        window,
        if (save) "Export settings" else "Import settings",
        if (save) FileDialog.SAVE else FileDialog.LOAD,
    )
    dialog.file = "MobiXournal-settings" + DesktopSettingsBackup.EXTENSION
    dialog.setFilenameFilter { _, name -> name.endsWith(DesktopSettingsBackup.EXTENSION, ignoreCase = true) }
    val chosen = awaitChoice(dialog) ?: return null
    if (!save) return chosen
    return if (chosen.name.contains('.')) chosen
    else File(chosen.parentFile, chosen.name + DesktopSettingsBackup.EXTENSION)
}

/** The native picker for a PDF to import — a name box, like the other browsers here. */
private fun choosePdf(window: Frame): File? {
    val dialog = FileDialog(window, "Import a PDF", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
    return awaitChoice(dialog)
}

/** The native save dialog with [proposed] already in its name field, or null when it was cancelled. */
private fun chooseSave(window: Frame, title: String, proposed: String): File? {
    val dialog = FileDialog(window, title, FileDialog.SAVE)
    dialog.file = proposed
    return awaitChoice(dialog)
}

/** Show [dialog] modally and answer the file it chose — null covers Cancel and an unnamed field alike. */
private fun awaitChoice(dialog: FileDialog): File? {
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}
