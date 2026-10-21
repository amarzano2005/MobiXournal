package com.mobixournal.desktop

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.window.FrameWindowScope
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
fun FrameWindowScope.DesktopApp(initial: File? = null) {
    val state = remember { DesktopShellState() }
    val focus = remember { FocusRequester() }

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

    LaunchedEffect(initial) { initial?.let(state::open) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // A tab holds a reader's file handle (a `.mobix` archive); closing the window closes them all.
    DisposableEffect(Unit) { onDispose { state.tabs.closeAll() } }

    DesktopTheme.Apply {
        DesktopShell(
            state = state,
            onOpenPicker = ::pick,
            modifier = Modifier
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event -> state.onKey(event, ::pick) },
        )
    }
}
