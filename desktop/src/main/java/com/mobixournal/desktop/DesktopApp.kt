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
    // Whether anything in the window holds the keyboard. A panel that closes takes its text field with
    // it, and Compose is then left with **no** focused element — the shortcuts would be dead until the
    // page was clicked. The window hands the keyboard back to its root instead, so `Ctrl+Z` or `G` works
    // the moment the panel is gone.
    var keyboardHeld by remember { mutableStateOf(false) }

    /** The system picker. `FileDialog`'s filter is ignored on Windows, so it is a convenience only. */
    fun pick() {
        val dialog = FileDialog(window, "Open a document", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            name.endsWith(".xopp", ignoreCase = true) || name.endsWith(".mobix", ignoreCase = true)
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) state.open(File(dir, name))
    }

    /**
     * Save As: the system dialog, proposing the name the document already has. The **container** follows
     * the suffix the user leaves on the name (`DesktopSaves.formatFor`) — `.mobix` for the native
     * archive, `.xopp` for the interchange one — so the dialog is where a format is chosen.
     */
    fun pickSaveDestination() {
        val tab = state.active ?: return
        val dialog = FileDialog(window, "Save the document as", FileDialog.SAVE)
        dialog.file = tab.file.name
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) state.saveAs(File(dir, name))
    }

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
    // The window's X asked the shell first (`Main`), and the shell says when every answer is in.
    LaunchedEffect(state.exitAllowed) { if (state.exitAllowed) onExit() }
    // A tab holds a reader's file handle (a `.mobix` archive); closing the window closes them all.
    DisposableEffect(Unit) { onDispose { state.tabs.closeAll() } }

    // The pen and the dial. Both are opened after the window exists and closed with it, and both answer
    // `null` on a host without them, which the status bar then says in words. The dial's events arrive on
    // Windows' own thread, so they are posted to the window's event queue before they touch any state.
    val tablet = remember { DesktopTablet() }
    DisposableEffect(Unit) {
        tablet.open(
            window = window,
            onRotation = { degrees -> EventQueue.invokeLater { state.dialZoom(degrees) } },
            onClick = { EventQueue.invokeLater { state.dialFit() } },
            log = { println("MobiXournal tablet: $it") },
        )
        onDispose { tablet.close() }
    }

    DesktopTheme.Apply {
        DesktopShell(
            state = state,
            onOpenPicker = ::pick,
            onSavePicker = ::pickSaveDestination,
            tablet = tablet,
            modifier = Modifier
                .focusRequester(focus)
                .focusable()
                .onFocusChanged { keyboardHeld = it.hasFocus }
                .onPreviewKeyEvent { event -> state.onKey(event, ::pick, ::pickSaveDestination) },
        )
    }
}
