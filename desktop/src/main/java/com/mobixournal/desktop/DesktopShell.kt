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

    /**
     * The drag in flight that `Esc` would cancel — a geometry tool's figure, while its pointer is still
     * down. The canvas sets it when the drag starts and clears it when the gesture ends; deliberately not
     * snapshot state, because nothing on screen reads it.
     */
    var liveDrag: (() -> Unit)? = null

    /** Whether the page sidebar is showing (`Ctrl+B`). */
    var sidebarVisible by mutableStateOf(true)

    /** True while `Space` is held: a primary-button drag pans instead of drawing. */
    var panMode by mutableStateOf(false)

    /** Page thumbnails, one map per document, kept by the shell so a scroll back is not a repaint. */
    val thumbnails = mutableMapOf<String, MutableMap<Int, ImageBitmap>>()

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

    /** The picked tool: what a primary drag on the page does (pen and highlighter ink, eraser rubs out). */
    var tools by mutableStateOf(ToolSelection())
        private set

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
    private fun edited(tab: DesktopTab, index: Int) {
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
        private set

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
        private set

    /**
     * What a lifted living-slot drag means: the family's question for a plot or a table, the placement
     * itself for a component (whose question was the palette).
     *
     * A placement that lands hands the window **to the selection**, exactly as the tablet does: the new
     * element is already what stays selected, so a tool still armed to place a second one would make the
     * very next drag place another plot instead of moving the one just made. The marquee is therefore
     * picked, and the next press drags, resizes or opens the pencil on what was placed.
     */
    fun placeOrAsk(tab: DesktopTab, slot: LivingSlot, bounds: Bounds) {
        if (slot == LivingSlot.CIRCUIT) {
            val element = livingElementFor(slot, bounds, ink, activeCircuitKind) ?: return
            if (placeLivingElement(tab, element) { index -> edited(tab, index) }) handToSelection()
            return
        }
        // A graph's first equation starts in the slot's ink until a curve is given its own colour.
        pendingPlacement = PendingPlacement(slot, bounds, tab.key, ink.colour)
    }

    /** Pick the selection tool, so the element just placed can be moved with the next press. */
    private fun handToSelection() {
        selectTool(DesktopTool.MARQUEE)
    }

    /** The question's answer: build the element from it and write it as **one** edit and one undo step. */
    fun confirmPlacement() {
        val pending = pendingPlacement ?: return
        val tab = tabs.active ?: return
        // The question was asked on a page; answering it on another tab would place the box of one
        // document on the sheet of another.
        if (tab.key != pending.tabKey) {
            pendingPlacement = null
            return
        }
        val element = pending.element(ink, activeCircuitKind) ?: return
        if (placeLivingElement(tab, element) { index -> edited(tab, index) }) {
            pendingPlacement = null
            // The same hand-over the circuit slot makes: the placed element is what stays selected, so
            // the next drag moves it rather than placing a second one.
            handToSelection()
        }
    }

    /** Drop the question without placing anything — the page is exactly what it was before the drag. */
    fun cancelPlacement() {
        pendingPlacement = null
    }

    /**
     * Which tool strips the window shows, mirrored from the settings file into snapshot state: the file
     * is where the choice *lives*, this is what the chrome reads, and [chooseToolPlacement] writes both —
     * so the chrome never has to poll a file and a restart still finds the placement it had.
     */
    var toolPlacement by mutableStateOf(settings.toolPlacement)
        private set

    /**
     * The two inks the window holds — the pen's and the highlighter's — restored from the settings file.
     *
     * Held as a pair and read through [ink], which answers for the **picked tool**: the strips, the canvas
     * and the selection's recolour must all show the ink that would be written with, and a second copy of
     * "the current ink" could only be a copy that disagrees with the tool.
     */
    private var inks by mutableStateOf(
        DesktopInks(
            pen = DesktopInk(colour = settings.inkColour, width = settings.inkWidth),
            highlighter = DesktopInk(colour = settings.highlighterColour, width = settings.highlighterWidth),
        ),
    )

    /** The ink of the picked tool — the pen's, or the highlighter's own colour and width. */
    val ink: DesktopInk get() = inks.forTool(tools.current)

    /**
     * The palette's editable custom slot, shared by both tools like the palette itself (a palette is what
     * the user has, not what a tool is). Null while it is empty.
     */
    var paletteCustom by mutableStateOf(settings.paletteCustom)
        private set

    /** What the colour panel is editing, or null while it is closed. */
    var editing by mutableStateOf<ColourEditorTarget?>(null)
        private set

    /** The palette the colour flyout offers; the factory eight until the settings file says otherwise. */
    var palette by mutableStateOf(settings.paletteColours)
        private set

    /** The three favourites of each tool, restored from the settings file. */
    private var favs by mutableStateOf(
        DesktopFavourites(pen = settings.colourFavourites, highlighter = settings.highlighterFavourites),
    )

    /** The terna the picked tool picks from — keys `1`–`3` and `Alt`+digit read this one. */
    val favourites: List<Int> get() = favs.forTool(tools.current)

    /** What both strips draw — one value, so the rail and the ribbon cannot show different inks. */
    val stripState: ToolStripState
        get() = ToolStripState(
            selection = tools,
            ink = ink,
            palette = palette,
            custom = paletteCustom,
            favourites = favourites,
            openGroup = openGroup,
            onOpenGroup = { group -> openGroup = group },
            circuitKinds = circuitKinds,
            circuitPickerOpen = circuitPickerOpen,
            onCircuitKind = ::pickCircuitKind,
            onCircuitPicker = ::showCircuitKinds,
        )

    /**
     * Pick a tool (a slot, a picker row or its accelerator); the canvas reads it on the next press.
     *
     * Picking also closes the group picker: its job was to choose, and it has.
     */
    fun selectTool(tool: DesktopTool) {
        // A text box is finished by leaving its tool: the typing is committed, never silently dropped.
        if (tool != DesktopTool.TEXT) textBox.commit(tabs.active, ::edited)
        tools = tools.select(tool)
        openGroup = null
    }

    /** `Alt + Down`: open the picked tool's group picker, or close the one that is up. */
    fun toggleToolPicker() {
        openGroup = toggledPicker(openGroup, tools)
    }

    /**
     * Pick the ink colour **of the picked tool**, and remember it: the pen and the highlighter keep their
     * own, so switching to the highlighter never repaints the pencil.
     */
    fun pickColour(colour: Int) {
        val tool = tools.current
        inks = inks.withInk(tool, inks.forTool(tool).withColour(colour))
        persistInk(tool)
    }

    /** Pick the picked tool's stroke width, and remember it. */
    fun pickWidth(width: Float) {
        val tool = tools.current
        inks = inks.withInk(tool, inks.forTool(tool).withWidth(width))
        persistInk(tool)
    }

    /** Write [tool]'s ink to the settings file — the other tool's entry is not touched. */
    private fun persistInk(tool: DesktopTool) {
        val value = inks.forTool(tool)
        if (tool == DesktopTool.HIGHLIGHTER) {
            settings.highlighterColour = value.colour
            settings.highlighterWidth = value.width
        } else {
            settings.inkColour = value.colour
            settings.inkWidth = value.width
        }
    }

    /** Pick favourite [index] — what keys `1`–`3` do. */
    fun pickFavourite(index: Int) {
        favourites.getOrNull(index)?.let(::pickColour)
    }

    /** Make the colour in use favourite [index] **of the picked tool** — what `Alt`+digit does. */
    fun setFavourite(index: Int) {
        val tool = tools.current
        val current = favs.forTool(tool)
        if (index !in current.indices) return
        favs = favs.withFavourites(tool, current.toMutableList().also { it[index] = ink.colour })
        if (tool == DesktopTool.HIGHLIGHTER) settings.highlighterFavourites = favs.highlighter
        else settings.colourFavourites = favs.pen
    }

    /**
     * Step the width one slot up ([direction] `+1`, `]`) or down (`-1`, `[`), stopping at the ends
     * rather than wrapping: a thin pen must not jump to the thickest one.
     */
    fun stepWidth(direction: Int) {
        val widths = PenPalette.DEFAULT_WIDTHS
        val slot = PenPalette.widthSlot(ink.width) + direction
        pickWidth(widths[slot.coerceIn(0, widths.lastIndex)])
    }


    /** Open the panel on the custom slot (`CUSTOM`) or on a colour to append (`NEW`). */
    fun editColour(target: ColourEditorTarget) {
        editing = target
    }

    /** Close the panel without committing anything. */
    fun cancelColourEdit() {
        editing = null
    }

    /**
     * Commit what the panel's **Set** handed back: the custom slot for [ColourEditorTarget.CUSTOM], a new
     * palette entry for [ColourEditorTarget.NEW] — and in both cases the colour becomes the ink, which is
     * what picking a swatch would have done.
     */
    fun commitColour(colour: Int) {
        when (editing) {
            ColourEditorTarget.CUSTOM -> {
                paletteCustom = colour
                settings.paletteCustom = colour
                pickColour(colour)
            }
            ColourEditorTarget.NEW -> {
                palette = PenPalette.add(palette, colour)
                settings.paletteColours = palette
                pickColour(colour)
            }
            null -> return
        }
        editing = null
    }

    /** Move the tool strips, and remember the choice across restarts. */
    fun chooseToolPlacement(placement: ToolPlacement) {
        toolPlacement = placement
        settings.toolPlacement = placement
    }

    /** The active document, if any. */
    val active: DesktopTab? get() = tabs.active

    /**
     * Open [file] as a tab (or activate its tab when it is already open). A failure is **reported as
     * itself** and leaves whatever was on screen alone: an unopenable file must not take a readable one
     * with it.
     */
    fun open(file: File) {
        runCatching { DocumentLoader.load(file) }
            .onSuccess { document ->
                tabs.open(document)
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
        sidebarVisible = !sidebarVisible
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
    fun onKey(event: KeyEvent, onOpenPicker: () -> Unit, onSavePicker: () -> Unit = {}): Boolean {
        // A held Space is a modifier, not a command: it starts on the press and ends on the release.
        if (event.key == Key.Spacebar && event.type == KeyEventType.KeyUp) {
            panMode = false
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        // A text box owns the keyboard: `Esc` commits it, and every other key is a character it types
        // (no `Space` to pan, no `B` for the pen, no `Delete` that deletes something other than a letter).
        textBox.command(event.key)?.let {
            textBox.commit(tabs.active, ::edited)
            return true
        }
        if (textBox.box != null) return false
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
        // So does a tool group's picker: a chevron that opened a list is closed by the key that closes
        // every other surface, not left hanging over the page.
        if (event.key == Key.Escape && openGroup != null) {
            openGroup = null
            return true
        }
        // `Alt + Down` is the picker's own key: it opens the picked tool's group and closes it again.
        // Checked before the shortcut table, where a bare `Down` means "next page".
        if (toolPickerKey(event.key, alt = event.isAltPressed)) {
            toggleToolPicker()
            return true
        }
        // The order is the table's: a `Ctrl` command first (`Ctrl+1` stays fit width), then the ink keys
        // (digits and brackets, none of which is a tool's letter), then the tools.
        val command = shellCommand(event.key, event.isCtrlPressed, event.isShiftPressed)
        if (command != null) return apply(command, onOpenPicker, onSavePicker)
        inkCommand(event.key, alt = event.isAltPressed, ctrl = event.isCtrlPressed)?.let { pressed ->
            when (pressed) {
                is InkCommand.PickFavourite -> pickFavourite(pressed.index)
                is InkCommand.SetFavourite -> setFavourite(pressed.index)
                is InkCommand.StepWidth -> stepWidth(pressed.direction)
            }
            return true
        }
        toolForKey(
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
    ): Boolean {
        when (command) {
            ShellCommand.DISMISS_FAILURE -> return dismissSurface()
            ShellCommand.OPEN -> {
                onOpenPicker()
                return true
            }
            ShellCommand.TOGGLE_SIDEBAR -> {
                toggleSidebar()
                return true
            }
            ShellCommand.SAVE_AS -> {
                if (tabs.active == null) return false
                onSavePicker()
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
        return when (command) {
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
            ShellCommand.SAVE, ShellCommand.SAVE_AS -> false
        }
    }

    /**
     * `Esc`, in the order the window's surfaces stack: the drag in flight, the component palette, the
     * failure card. A surface that is not up answers false, so the key is not swallowed by nothing.
     */
    private fun dismissSurface(): Boolean {
        // A drag that has not finished is the first thing `Esc` answers: cancelling the figure being
        // drawn is the only way back from a geometry gesture the pointer has not released.
        val cancel = liveDrag
        if (cancel != null) {
            liveDrag = null
            cancel()
            return true
        }
        // The placement question is newer than the palette and older than the failure card.
        if (pendingPlacement != null) {
            pendingPlacement = null
            return true
        }
        if (circuitPickerOpen != null) {
            circuitPickerOpen = null
            return true
        }
        if (failure == null) return false
        failure = null
        return true
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
