package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The shell as the drawing in `docs/ui-modernization.md` lays it out: a header carrying the tab strip,
 * a sidebar in a lane of its own beside the canvas, the page on the desk, and the status bar under it.
 *
 * The bands are **glass, and the page is not underneath them**: the header and the status bar take their
 * own rows and the canvas takes the rectangle that is left, so no band ever lies on the sheet
 * (`docs/chrome.md`). Everything that floats — the failed-open card — may sit over the page, because it
 * comes and goes with a gesture. The two jobs that wait for the edits to go quiet are [DesktopQuiet.kt]'s.
 *
 * It lives apart from [DesktopShellState] because they are two jobs — the window's state and commands, and
 * the rows that draw them — and because `SourceStructureTest` keeps both files inside the budget.
 */
@Composable
internal fun DesktopShell(
    state: DesktopShellState,
    onOpenPicker: () -> Unit,
    /** The system Save As picker, opened from the menu and from `Ctrl+Shift+S`. */
    onSavePicker: () -> Unit = {},
    /** The PDF picker behind the menu's **Import PDF** row (`ImportPdfDialog` asks where the pages go). */
    onImportPdfPicker: () -> Unit = {},
    /** Where the export's PDF should go, opened from the menu and from `Ctrl+E`. */
    onExportPdfPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** The window's devices, or null in a test that has none — the canvas then keeps the mouse path. */
    tablet: DesktopTablet? = null,
) {
    ThumbnailRefresh(state)
    RecoverySnapshots(state)
    // Which document is on screen decides whether the audio pill has something to play: asked when the
    // tab changes, never per frame, because it walks the pages.
    LaunchedEffect(state.active?.key) {
        DesktopAudioSession.noteAudioPresence(state.active?.document?.model)
    }
    // The one export in flight, written off the UI thread (`DesktopExportCommands.kt`).
    ExportWorker(state)
    Column(modifier.fillMaxSize().background(DesktopTheme.desk)) {
        Header(
            tabs = state.tabs,
            sidebarVisible = state.sidebarVisible,
            onActivate = { index -> state.tabs.activate(index) },
            onClose = { index -> state.requestCloseTab(index) },
            onMove = { from, to -> state.tabs.move(from, to) },
            onNew = { state.newDocument() },
            onOpen = onOpenPicker,
            onSave = { state.save() },
            onSaveAs = onSavePicker,
            onExportPdf = onExportPdfPicker,
            onImportPdf = onImportPdfPicker,
            // The pages go beside the document, which is the folder the user opened it from; no dialog,
            // because AWT has no folder-only picker and a file picker would ask the wrong question.
            onExportSvg = { DesktopExportQueue.request(ExportRequest.Svg()) },
            // The raster of the same pages, one PNG each, through the same worker.
            onExportPng = { DesktopExportQueue.request(ExportRequest.Png()) },
            // The page's two commands live in the tool strips now, after their divider
            // (`ToolStrips.PageSetupSlots`): the header keeps the file and the view.
            onToggleSidebar = { state.toggleSidebar() },
            // The two history steps and the two file commands are the same commands the keys and the
            // menu reach: a header button is a third door, never a third implementation.
            onUndo = { state.apply(ShellCommand.UNDO) },
            onRedo = { state.apply(ShellCommand.REDO) },
            onSettings = { state.apply(ShellCommand.OPEN_SETTINGS) },
            // The menu's Pen block: its heading opens the Stylus page, and the pressure rows and presets
            // below it are that page's own answers, reached without leaving the sheet.
            onPenParameters = { state.openPenSettings() },
            // The menu's own pressure rows: the same settings the Stylus page edits.
            penPolicy = state.prefs,
            // The recording pill, left of Save: record, pause, play, the take's clock and its level.
            audio = DesktopAudioSession,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The page is always drawn: Settings is a **floating surface** over it, not a second window
            // and not a page of its own. The reader keeps the document in view while a preference is
            // changed — which is the point of changing it (`docs/interface-contract.md` — a surface that
            // comes and goes may lie over the page; only a band may not).
            WorkspaceBody(state, tablet)
            if (state.prefs.open) SettingsSurface(state.prefs)
        }
        StatusBar(state.active, tablet, state.prefs.frameTime, DesktopAudioSession)
    }
}

/**
 * The document's own half: the notice when nothing is open, the workspace when something is, and the
 * floating surfaces the window raises over the page.
 *
 * Apart from [DesktopShell] so the Settings branch is one `if` at the top rather than a condition threaded
 * through every card the page can raise (`SourceStructureTest` keeps both files inside the budget).
 */
@Composable
private fun BoxScope.WorkspaceBody(state: DesktopShellState, tablet: DesktopTablet?) {
    val tab = state.active
    when {
        // With nothing open the notice owns the window; with a document open the card floats over it
        // instead, so an unopenable file cannot hide the one you were reading.
        tab == null && state.failure != null -> Notice(state.failure!!.title, state.failure!!.detail)
        tab == null -> Notice(
            "Open a .xopp or .mobix file to start",
            "Ctrl+T, or “Open…”. The preview shows paper, ink, living entities and PDF or " +
                "image backgrounds, read-only.",
        )
        // The strips in their lanes, the sidebar and the page: `DesktopWorkspace.kt`.
        else -> PageWorkspace(state, tab, tablet)
    }
    if (tab != null) {
        state.failure?.let { reported ->
            OpenFailureCard(
                failure = reported,
                onDismiss = { state.dismissFailure() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            )
        }
    }
    ColourPanelHost(state)
    PageSetupHost(state)
    ImportPdfHost(state)
    PageRangeHost(state)
    CloseDialogHost(state)
}
