package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.render.Bounds
import com.mobixournal.render.CircuitFamily
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.ShapeKind
import java.io.File

/**
 * The window's content, and everything it needs to be driven — **without a window**.
 *
 * This is the piece that used to live inside `DesktopApp`, tangled with `FrameWindowScope`, a
 * `FileDialog` and a focus requester, which is why the whole shell could only be checked by opening it
 * on a desktop. Here it is an ordinary state class plus a composable, so a headless render can paint the
 * real header, the real sidebar and the real status bar and a test can press the real keys.
 *
 * One state, read by the chrome, is also the rule that keeps a readout from disagreeing with itself:
 * the active document is [DesktopTabs.active], never a second copy of it, so opening, closing and
 * switching cannot drift apart.
 */
class DesktopShellState(
    /** The window's settings file: the tool placement today, the Interface section once it has a window. */
    private val settings: DesktopSettings = DesktopSettings(),
    /**
     * The crash snapshots dirty tabs are written to, or null for a shell that must not touch the disk
     * (a render or command test). The window passes [DesktopRecovery] and nothing else does.
     */
    val recovery: DesktopRecovery? = null,
) {

    /** The open documents and which one is active. */
    val tabs = DesktopTabs()

    /**
     * The close the window is asking about, or null while nothing is being asked ([CloseRequest]).
     *
     * It lives on the shell, not on the tab, because the answers — Save, Discard, Cancel — are the
     * window's decisions: a tab cannot close itself without them.
     */
    var closeRequest: CloseRequest? by mutableStateOf(null)
        internal set

    /** Set once every dirty tab has been answered: the window's X may go on and close it. */
    var exitAllowed: Boolean by mutableStateOf(false)
        internal set

    /**
     * Bumped by every committed edit, so the shell can write a crash snapshot after a **quiet** burst
     * rather than per stroke ([flushRecovery]). Not the dirty state itself: that is the tab's save point.
     */
    var editRevision: Int by mutableStateOf(0)
        internal set

    /**
     * Bumped **after** a burst of edits goes quiet ([ThumbnailRefresh]), and the only key the sidebar
     * re-renders a page's thumbnail on — separate from [editRevision] because a thumbnail is expensive in a
     * way a stroke is not (`DesktopQuiet.kt` owns the reasoning and the two quiet periods).
     */
    var thumbnailRevision: Int by mutableStateOf(0)
        internal set

    /** The last failed open, or null — cleared on the next successful one or by [dismissFailure]. */
    var failure by mutableStateOf<OpenFailure?>(null)

    /** The PDF import being asked about, or null — the file, its pages and the answer in play. */
    var importRequest by mutableStateOf<ImportPdfRequest?>(null)
        internal set

    /**
     * The drag in flight that `Esc` would cancel — a geometry tool's figure, while its pointer is still
     * down. The canvas sets it when the drag starts and clears it when the gesture ends; deliberately not
     * snapshot state, because nothing on screen reads it.
     */
    var liveDrag: (() -> Unit)? = null

    /** The page list's lane and its one rule: a document of more than one page shows it (`DesktopSidebar`). */
    private val sidebar = DesktopSidebar()

    /** Whether the page list is showing (`Ctrl+B`) — computed, so opening a file needs no second step. */
    val sidebarVisible: Boolean get() = sidebar.shows(active, prefs.sidebarPolicy)

    /** True while `Space` is held: a primary-button drag pans instead of drawing. */
    var panMode by mutableStateOf(false)

    /**
     * Page thumbnails, one map per document, kept so a scroll back is not a repaint. Concurrent because
     * the maps are read where the picture is painted (the sidebar's own thread, `ThumbnailPaint`) while
     * an edit drops the page it just changed on the UI thread.
     */
    val thumbnails = java.util.concurrent.ConcurrentHashMap<String, MutableMap<Int, ImageBitmap>>()

    /**
     * The window's clipboard — the tablet's Copy, fulfilled in memory rather than through the system's
     * (which would hand another application a document the user did not export).
     */
    val clipboard = DesktopClipboard()

    /**
     * The living element whose editor is open, or null — the pencil on the selection bar and a
     * double-click on the element both set it. The panel reads the element itself through the tab, so
     * this is a handle to **what** is being edited, not a copy of it.
     */
    var editingLiving by mutableStateOf<LivingTargetRef?>(null)
        private set

    /** The Text tool's box: the draft in `DesktopTextBox.kt`, drawn in place by the canvas. */
    val textBox = DesktopTextBox()

    /**
     * Let the sidebar paint the pages it shows again — the debounced half of [thumbnailRevision].
     *
     * Called from [ThumbnailRefresh] and by undo/redo: a stale cached bitmap is dropped where the edit
     * happens (`DesktopWorkspace`), so the re-render finds the page it must draw.
     */
    fun refreshThumbnails() {
        thumbnailRevision++
    }

    /** The pick, and the file that remembers it ([DesktopToolPicks]). */
    private val picks = DesktopToolPicks(settings)

    /** The picked tool — what a primary drag does. Restored from the file, with that slot's ink. */
    val tools: ToolSelection get() = picks.selection

    /**
     * Do what one bar button says on the active tab. `EDIT` is the shell's own (it opens a panel), so it
     * is answered here and never reaches [performSelection].
     */
    fun runSelection(action: SelectionAction) {
        val tab = tabs.active ?: return
        if (action == SelectionAction.EDIT) {
            editingLiving = livingTargetOf(tab)?.let { LivingTargetRef(tab.key, tab.page, it) }
            return
        }
        performSelection(tab, action, ink, clipboard) { index -> edited(tab, index) }
    }

    /** Close the living element's editor. */
    fun closeLivingEditor() {
        editingLiving = null
    }

    /** Write a field's answer into the element the lane is editing — one edit, one undo step. */
    fun commitLiving(element: Element) {
        val target = editingLiving ?: return
        val tab = tabs.active ?: return
        replaceLiving(tab, target, element) { index -> edited(tab, index) }
    }

    /** The same quiet period every edit starts: the page's picture is stale and the snapshot waits. */
    internal fun edited(tab: DesktopTab, index: Int) {
        thumbnails[tab.key]?.remove(index)
        noteEdit()
    }

    /**
     * Which tool group's picker is up, as an index into [DESKTOP_TOOL_GROUPS]; null when none is.
     *
     * View state, like the sidebar's visibility: nothing about the document changes when a picker opens,
     * and the *selection* it acts on is [tools]. It lives here rather than inside a strip so the keyboard
     * and the chevron open the same one and `Esc` can close it.
     */
    var openGroup by mutableStateOf<Int?>(null)
        private set

    /**
     * The part each **circuit slot** is armed with, one per family.
     *
     * The window's half of the tablet's three circuit palettes: a press on the page places the armed part
     * of that slot's family. View state, not document state — the element carries its own `symbol`, so this
     * only decides what the *next* placement is. Each family starts on its own face ([CircuitFamily.face]).
     */
    var circuitKinds by mutableStateOf(CircuitFamily.entries.associateWith { it.face })
        private set

    /** The family whose palette is open (`CircuitKindPicker`), or null when none is. */
    var circuitPickerOpen by mutableStateOf<CircuitFamily?>(null)
        // Written by the shell's own commands and cleared by `WindowEscape`, which is a separate file
        // only because this one is at its structural limit.
        internal set

    /** Arm [family]'s slot with [kind]; the palette closes, because the question is answered. */
    fun pickCircuitKind(family: CircuitFamily, kind: ShapeKind) {
        circuitKinds = circuitKinds + (family to kind)
        circuitPickerOpen = null
    }

    /** Open [family]'s palette, or close the open one with null. */
    fun showCircuitKinds(family: CircuitFamily?) {
        circuitPickerOpen = family
    }

    /** The part the picked slot would place: its family's arm, or the passive palette's face. */
    val activeCircuitKind: ShapeKind
        get() = circuitFamilyOf(tools.current)
            ?.let { family -> circuitKinds[family] ?: family.face }
            ?: CircuitFamily.PASSIVE.face

    /**
     * A placement that is **asking its values** before it exists, or null while no question is up.
     *
     * The tablet asks first — a graph dialog, a table grid, a component palette — so this lane does too,
     * and nothing is written until the answer arrives: a cancelled question leaves no element and no undo
     * step behind. Only the families with values to ask use it; a component's part was the palette's
     * answer, so a circuit places at once.
     */
    var pendingPlacement by mutableStateOf<PendingPlacement?>(null)
        internal set

    // What a lifted drag *does* with it — ask, answer, drop — is `DesktopShellPlacement.kt`, beside the
    // command surface rather than in the middle of it.

    /**
     * The window's **Settings**: the surface's own state and every choice that outlives a restart
     * (`DesktopSettingsState`). The chrome reads the values through this one object, so a row on the
     * surface is the same value the strips, the sidebar and the status bar draw.
     */
    val prefs = DesktopSettingsState(settings)

    init {
        // A settings backup replaces the file; the inks and the palette this shell holds are refreshed
        // with it, so an import is visible on the page rather than only on the settings rows.
        prefs.onRestored = { inkState.reload() }
    }

    /** The menu's *Parameters…*: the Settings surface, open on the pen's own page. */
    fun openPenSettings() = prefs.openAt(DesktopSettingSection.STYLUS)

    /**
     * The window's ink and palette, its own piece of the state, owned here and forwarded below so every
     * caller keeps reading `state.ink` and `state.pickColour(…)` (`DesktopInkState`).
     */
    private val inkState = DesktopInkState(settings) { tools.current }

    /** The ink of the picked tool — the pen's, or the highlighter's colour and width **slot**. */
    val ink: DesktopInk get() = inkState.ink

    /** [ink] as the page is written with it: a highlighter's slot read as a marker's tip (`DesktopInkState.pageInk`). */
    val pageInk: DesktopInk get() = inkState.pageInk

    /**
     * The pen's ink, whatever tool is in hand: a figure, a circuit or a placement writes with this one.
     *
     * The width is the Figures section's own answer, not the pen's slot: a figure has no pressure, so its
     * width is chosen once and drawn at full size — which is how a rectangle is made to match a light pen
     * stroke instead of the pen's widest slot.
     */
    val shapeInk: DesktopInk get() = inkState.shapeInk.withWidth(prefs.figureWidth)

    /** The palette's editable custom slot, shared by both tools. Null while it is empty. */
    val paletteCustom: Int? get() = inkState.paletteCustom

    /** What the colour panel is editing, or null while it is closed. */
    val editing: ColourEditorTarget? get() = inkState.editing

    /** The palette the colour flyout offers. */
    val palette: List<Int> get() = inkState.palette

    /** The terna the picked tool picks from — keys `1`–`3` and `Alt`+digit read this one. */
    val favourites: List<Int> get() = inkState.favourites

    /** What both strips draw — one value, so the rail and the ribbon cannot show different inks. */
    val stripState: ToolStripState
        get() = ToolStripState(
            selection = tools,
            ink = ink,
            palette = palette,
            custom = paletteCustom,
            favourites = favourites,
            openGroup = openGroup,
            // The Toolbar section's arrangement, so a button moved on the settings page moves in both
            // strips — and the picker's index means the same slot in each of them.
            groups = prefs.toolGroups,
            onOpenGroup = { group -> openGroup = group },
            circuitKinds = circuitKinds,
            circuitPickerOpen = circuitPickerOpen,
            onCircuitKind = ::pickCircuitKind,
            onCircuitPicker = ::showCircuitKinds,
            // The page's own menu: a command of the page, not of the ink beside it.
            pages = pagesSection.actions(
                { openPageSetup(PageSetupSection.PAPER) },
                { openPageSetup(PageSetupSection.FORMAT) },
            ),
        )

    /**
     * Pick a tool (a slot, a picker row or its accelerator); the canvas reads it on the next press.
     *
     * Picking also closes the group picker: its job was to choose, and it has.
     */
    fun selectTool(tool: DesktopTool) {
        // A text box is finished by leaving its tool: the typing is committed, never silently dropped.
        if (tool != DesktopTool.TEXT) textBox.commit(tabs.active, ::edited)
        picks.pick(tool)
        openGroup = null
    }

    /** A **toggle** press: pick the family's tool, or put back the last one ([toggledTool]). */
    fun toggleTool(target: DesktopTool) {
        selectTool(picks.toggleTarget(target))
    }

    /** `Alt + Down`: open the picked tool's group picker, or close the one that is up. */
    fun toggleToolPicker() {
        openGroup = toggledPicker(openGroup, tools)
    }

    /**
     * Pick the ink colour **of the picked tool**, and remember it: the pen and the highlighter keep their
     * own, so switching to the highlighter never repaints the pencil.
     */
    /** Pick the picked tool's ink colour, and remember it — what [DesktopInkState] keeps per tool. */
    /** The menu's one pen preset: the tool in hand back on its factory colour, width and plain line. */
    fun pickColour(colour: Int) = inkState.pickColour(colour)

    /** Pick the picked tool's stroke width, and remember it. */
    fun pickWidth(width: Float) = inkState.pickWidth(width)

    /** Pick the picked tool's stroke pattern (solid, dashed, dash-dot or dotted), and remember it. */
    fun pickLineStyle(style: LineStyle) = inkState.pickLineStyle(style)

    /** Pick favourite [index] — what keys `1`–`3` do. */
    fun pickFavourite(index: Int) = inkState.pickFavourite(index)

    /** Make the colour in use favourite [index] of the picked tool — what `Alt`+digit does. */
    fun setFavourite(index: Int) = inkState.setFavourite(index)

    /** Step the width one slot up (`+1`, `]`) or down (`-1`, `[`), stopping at the ends. */
    fun stepWidth(direction: Int) = inkState.stepWidth(direction)

    /** Open the colour panel on the custom slot, or on a colour to append to the palette. */
    fun editColour(target: ColourEditorTarget) = inkState.editColour(target)

    /** Close the panel without committing anything. */
    fun cancelColourEdit() = inkState.cancelColourEdit()

    /** Commit the colour the panel handed back: the custom slot, or a new palette entry, as the ink. */
    fun commitColour(colour: Int) = inkState.commitColour(colour)

    /** The active document, if any. */
    val active: DesktopTab? get() = tabs.active

    /** The page setup's own piece of the state: which section is up, and the edits it makes. */
    private val pageSetupState = DesktopPageSetupState(tabs) { tab, index -> edited(tab, index) }
    /** The page **list**'s commands — add, remove, step (`DesktopPages.kt`). */
    internal val pagesSection = DesktopPages(tabs, ::edited, { defaultPageSize }) { pagesChanged(it) }

    /** The section of the page-setup surface that is up, or null while it is closed. */
    val pageSetup: PageSetupSection? get() = pageSetupState.section

    /** Open the page-setup surface on its paper or its format section. */
    fun openPageSetup(section: PageSetupSection) = pageSetupState.open(section)

    /** Close the page-setup surface without touching the page again. */
    fun closePageSetup() = pageSetupState.close()

    /** The active page as it is now, or null when nothing is open. */
    fun activePage(): Page? = pageSetupState.activePage()

    /** Change the active page's paper style — one edit, one undo step. */
    fun setPageStyle(style: String) = pageSetupState.setStyle(style)

    /** Change the active page's ruling spacing, in millimetres, as desktop's own `r1` parameter. */
    fun setPageSpacingMm(mm: Double) = pageSetupState.setSpacingMm(mm)

    /** Resize the active page, keeping its ink and its paper. */
    fun setPageSize(widthPt: Double, heightPt: Double) = pageSetupState.setSize(widthPt, heightPt)

    /** The default sheet: the format section's **Default** entry, and what a new page is born on. */
    private val defaultSheet = DefaultSheet(settings)

    val defaultPageSize: Pair<Double, Double> get() = defaultSheet.size

    /** Store the active page's size as the default for every new sheet (a setting, not a page edit). */
    fun setDefaultPageSize(widthPt: Double, heightPt: Double) = defaultSheet.store(widthPt, heightPt)

    /**
     * A new page: a blank document tab, its file still to be written, on the **default sheet** the format
     * section stored — so *Set as default* is a promise the next new page keeps.
     */
    fun newDocument() {
        tabs.newBlankDocument(settings.pageWidthPt, settings.pageHeightPt, prefs.newDocumentFormat)
        failure = null
    }

    /**
     * What the window shows at startup: recovered edits first, then the file named on the command line,
     * and otherwise a **new page** rather than an empty notice — the default screen is something to write
     * on.
     */
    fun start(initial: File?) {
        recover()
        if (initial != null) {
            open(initial)
        } else if (tabs.isEmpty) {
            newDocument()
        }
    }

    /**
     * Open [file] as a tab (or activate its tab when it is already open). A failure is **reported as
     * itself** and leaves whatever was on screen alone: an unopenable file must not take a readable one
     * with it.
     */
    fun open(file: File) {
        runCatching { DocumentLoader.load(file) }
            .onSuccess { document ->
                tabs.open(document, prefs.fitOnOpen)
                failure = null
            }
            .onFailure { failure = describeOpenFailure(it) }
    }

    /** Close the active tab, activating its neighbour. A dirty tab must go through [requestCloseActive]. */
    fun closeActive() {
        tabs.closeActive()
    }

    fun dismissFailure() {
        failure = null
    }

    fun toggleSidebar() {
        sidebar.toggle(active, prefs.sidebarPolicy)
    }

    /**
     * The window's keys, every one of them also in the shortcuts table of `docs/ui-modernization.md`.
     *
     * Takes the picker as a callback because opening the system file dialog is the window's job — this
     * class has no window to hang one on.
     *
     * The event is only *translated* here; what the key means is [shellCommand] and what it does is
     * [apply]. Compose's `KeyEvent` cannot be built outside a real window, so keeping the decision out
     * of it is what lets the shortcuts be tested at all.
     */
    fun onKey(event: KeyEvent, onOpenPicker: () -> Unit, onSavePicker: () -> Unit = {}, onExportPicker: () -> Unit = {}): Boolean {
        // A held Space is a modifier, not a command: it starts on the press and ends on the release.
        if (event.key == Key.Spacebar && event.type == KeyEventType.KeyUp) {
            panMode = false
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        // A Shortcuts row waiting for a key takes the next press: the window sees a press before any row can.
        if (prefs.armedShortcut != null) {
            if (event.key == Key.Escape) prefs.cancelArmed()
            else ShortcutTable.name(event.key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed)
                ?.let(prefs::bindArmed)
            return true
        }
        // A text box owns the keyboard: `Esc` commits it, every other key is a character it types.
        textBox.command(event.key)?.let {
            textBox.commit(tabs.active, ::edited)
            return true
        }
        if (textBox.box != null) return false
        // The Settings surface owns the keyboard while it is up: `Esc` clears its search or closes it, and
        // nothing on the page underneath is a command while a page it cannot see is covered.
        if (prefs.onKey(event)) return true
        // A question owns `Esc` before anything else: Cancel is the answer that loses nothing.
        if (event.key == Key.Escape && closeRequest != null) {
            cancelClose()
            return true
        }
        // A living element's editor owns `Esc` first: the lane closes before a selection or a message.
        if (event.key == Key.Escape && editingLiving != null) {
            closeLivingEditor()
            return true
        }
        // A selection owns `Esc` while there is one: the outline goes before a message is dismissed.
        if (event.key == Key.Escape && dismissSelection(tabs.active)) return true
        // A colour panel that is up owns `Esc`: it closes before anything else the key might mean.
        if (event.key == Key.Escape && editing != null) {
            cancelColourEdit()
            return true
        }
        // So does a tool group's picker: a chevron that opened a list must not hang over the page.
        if (event.key == Key.Escape && openGroup != null) {
            openGroup = null
            return true
        }
        // `Alt + Down` is the picker's key, checked before the table where bare `Down` means "next page".
        if (toolPickerKey(event.key, alt = event.isAltPressed)) {
            toggleToolPicker()
            return true
        }
        // The order is the table's: a `Ctrl` command first (`Ctrl+1` stays fit width), then the ink keys
        // (digits and brackets, none of which is a tool's letter), then the tools.
        // The window's own table (`interface.shortcuts` laid over the factory rows).
        val keys = prefs.shortcutTable
        val command = shellCommand(keys, event.key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed)
        if (command != null) return apply(command, onOpenPicker, onSavePicker, onExportPicker)
        inkCommand(event.key, alt = event.isAltPressed, ctrl = event.isCtrlPressed)?.let { pressed ->
            when (pressed) {
                is InkCommand.PickFavourite -> pickFavourite(pressed.index)
                is InkCommand.SetFavourite -> setFavourite(pressed.index)
                is InkCommand.StepWidth -> stepWidth(pressed.direction)
            }
            return true
        }
        toolForKey(
            keys,
            event.key,
            shift = event.isShiftPressed,
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
        )?.let { tool ->
            selectTool(tool)
            return true
        }
        return false
    }

    /**
     * Do what [command] says, on the active document where it needs one.
     *
     * Opening, the sidebar toggle and dismissing a message work with **nothing open** — `Ctrl+T` used to
     * be reachable only from an already-open document, which is exactly backwards for the one shortcut
     * that gets a first document onto the screen.
     */
    fun apply(
        command: ShellCommand,
        onOpenPicker: () -> Unit = {},
        onSavePicker: () -> Unit = {},
        onExportPicker: () -> Unit = {},
    ): Boolean {
        when (command) {
            ShellCommand.DISMISS_FAILURE -> return WindowEscape.dismiss(this)
            ShellCommand.OPEN_SETTINGS -> {
                prefs.openAt()
                return true
            }
            ShellCommand.OPEN -> {
                onOpenPicker()
                return true
            }
            ShellCommand.TOGGLE_SIDEBAR -> {
                toggleSidebar()
                return true
            }
            // No document needed: the window takes the desk, and the page below it stays as it is.
            // The placement and the display's mode are the window's to change (`DesktopApp`), which is
            // why this is a flag here and an effect there.
            ShellCommand.TOGGLE_FULLSCREEN -> {
                prefs.toggleFullScreen()
                return true
            }
            // The three switches need no document: they only change what a drag would do.
            ShellCommand.TOGGLE_ERASER, ShellCommand.TOGGLE_HAND, ShellCommand.TOGGLE_SELECTION -> {
                toggleTool(
                    when (command) {
                        ShellCommand.TOGGLE_ERASER -> prefs.toggleEraserTool
                        ShellCommand.TOGGLE_HAND -> DesktopTool.HAND
                        else -> prefs.toggleSelectionTool
                    },
                )
                return true
            }
            // Both ask the window where the file goes: this class has no dialog to open.
            ShellCommand.SAVE_AS, ShellCommand.EXPORT_PDF -> {
                if (tabs.active == null) return false
                if (command == ShellCommand.SAVE_AS) onSavePicker() else onExportPicker()
                return true
            }
            // Ctrl+S is **unavailable** on a tab whose file already holds it: there is nothing to
            // write, and a command that does nothing must say so by not being handled (the state a
            // header control greys out on). Save As stays available — a clean document can still be
            // written somewhere else.
            ShellCommand.SAVE -> {
                val tab = tabs.active ?: return false
                if (!tab.isDirty) return false
                save()
                return true
            }
            else -> Unit
        }
        val tab = tabs.active ?: return false
        return applyOnTab(tab, command)
    }

    /** The commands that act on a document: one `when` over the active [tab]'s own state. */
    private fun applyOnTab(tab: DesktopTab, command: ShellCommand): Boolean {
        return when (command) {
            // Answered above, before a tab is needed: Settings opens with nothing on screen.
            ShellCommand.OPEN_SETTINGS -> true
            ShellCommand.CLOSE_TAB -> {
                requestCloseActive(); true
            }
            ShellCommand.NEXT_TAB -> {
                tabs.next(); true
            }
            ShellCommand.PREVIOUS_TAB -> {
                tabs.previous(); true
            }
            ShellCommand.ZOOM_IN -> {
                zoomBy(tab, ZOOM_STEP); true
            }
            ShellCommand.ZOOM_OUT -> {
                zoomBy(tab, 1f / ZOOM_STEP); true
            }
            ShellCommand.FIT_PAGE -> {
                tab.fit = FitMode.PAGE
                tab.viewport = tab.viewport.copy(offsetX = 0f, offsetY = 0f)
                true
            }
            ShellCommand.FIT_WIDTH -> {
                tab.fit = FitMode.WIDTH
                tab.viewport = tab.viewport.copy(offsetX = 0f, offsetY = 0f)
                true
            }
            ShellCommand.NEXT_PAGE -> {
                selectPage(tab, (tab.page + 1).coerceAtMost(tab.pageCount - 1)); true
            }
            ShellCommand.PREVIOUS_PAGE -> {
                selectPage(tab, (tab.page - 1).coerceAtLeast(0)); true
            }
            ShellCommand.START_PAN -> {
                panMode = true; true
            }
            // Undo and redo are the tab's own history, so they are **unavailable** — not merely no-ops —
            // while it is empty, which is the state a header control greys the command out on.
            ShellCommand.UNDO -> stepHistory(tab, redo = false)
            ShellCommand.REDO -> stepHistory(tab, redo = true)
            // `Delete` removes what the marquee caught, as one edit and one undo step; with nothing
            // selected the key is not handled, so a keyboard shortcut never deletes by accident.
            ShellCommand.DELETE_SELECTION -> deleteSelection(tab) { index -> edited(tab, index) }
            // The clipboard commands are the bar's own, reached from the keyboard. A copy of nothing is
            // not handled at all — the state the bar draws its button greyed out on.
            ShellCommand.CUT_SELECTION -> cutSelection(tab, clipboard) { index -> edited(tab, index) }
            ShellCommand.COPY_SELECTION -> copySelection(tab, clipboard)
            ShellCommand.PASTE_CLIPBOARD -> pasteClipboard(tab, clipboard) { index -> edited(tab, index) }
            ShellCommand.DUPLICATE_SELECTION -> duplicateSelection(tab) { index -> edited(tab, index) }
            // Handled above, with no document needed.
            ShellCommand.DISMISS_FAILURE, ShellCommand.OPEN, ShellCommand.TOGGLE_SIDEBAR,
            ShellCommand.TOGGLE_FULLSCREEN, ShellCommand.SAVE, ShellCommand.SAVE_AS,
            ShellCommand.EXPORT_PDF, ShellCommand.TOGGLE_ERASER, ShellCommand.TOGGLE_HAND,
            ShellCommand.TOGGLE_SELECTION -> false
        }
    }

    /**
     * One step through [tab]'s history, as a command: the cached thumbnails go with it, because a step may
     * restore a page's picture, and the step starts the same quiet period an edit does — the restored pages
     * reach the sidebar there, and the document is dirty again either way.
     */
    private fun stepHistory(tab: DesktopTab, redo: Boolean): Boolean {
        if (!(if (redo) tab.redo() else tab.undo())) return false
        thumbnails[tab.key]?.clear()
        noteEdit()
        return true
    }
}
