package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.SideEffect
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
) {

    /** The open documents and which one is active. */
    val tabs = DesktopTabs()

    /** The last failed open, or null — cleared on the next successful one or by [dismissFailure]. */
    var failure by mutableStateOf<OpenFailure?>(null)

    /** Whether the page sidebar is showing (`Ctrl+B`). */
    var sidebarVisible by mutableStateOf(true)

    /** True while `Space` is held: a primary-button drag pans instead of drawing. */
    var panMode by mutableStateOf(false)

    /** Page thumbnails, one map per document, kept by the shell so a scroll back is not a repaint. */
    val thumbnails = mutableMapOf<String, MutableMap<Int, ImageBitmap>>()

    /** The picked tool. Nothing draws with it yet: the strips only set the selection. */
    var tools by mutableStateOf(ToolSelection())
        private set

    /**
     * Which tool strips the window shows, mirrored from the settings file into snapshot state: the file
     * is where the choice *lives*, this is what the chrome reads, and [chooseToolPlacement] writes both —
     * so the chrome never has to poll a file and a restart still finds the placement it had.
     */
    var toolPlacement by mutableStateOf(settings.toolPlacement)
        private set

    /** The colour, width and custom slot the strips show, restored from the settings file. */
    var ink by mutableStateOf(
        DesktopInk(
            colour = settings.inkColour,
            width = settings.inkWidth,
            custom = settings.paletteCustom,
        ),
    )
        private set

    /** What the colour panel is editing, or null while it is closed. */
    var editing by mutableStateOf<ColourEditorTarget?>(null)
        private set

    /** The palette the colour flyout offers; the factory eight until the settings file says otherwise. */
    var palette by mutableStateOf(settings.paletteColours)
        private set

    /** The three colours keys `1`–`3` pick, and `Alt`+digit redefines. */
    var favourites by mutableStateOf(settings.colourFavourites)
        private set

    /** What both strips draw — one value, so the rail and the ribbon cannot show different inks. */
    val stripState: ToolStripState
        get() = ToolStripState(selection = tools, ink = ink, palette = palette, favourites = favourites)

    /** Pick a tool (a slot, or its accelerator): the only thing the strips do until the editing phase. */
    fun selectTool(tool: DesktopTool) {
        tools = tools.select(tool)
    }

    /** Pick the ink colour, and remember it: the same swatch is there after a restart. */
    fun pickColour(colour: Int) {
        ink = ink.withColour(colour)
        settings.inkColour = ink.colour
    }

    /** Pick a stroke width, and remember it. */
    fun pickWidth(width: Float) {
        ink = ink.withWidth(width)
        settings.inkWidth = ink.width
    }

    /** Pick favourite [index] — what keys `1`–`3` do. */
    fun pickFavourite(index: Int) {
        favourites.getOrNull(index)?.let(::pickColour)
    }

    /** Make the colour in use favourite [index] — what `Alt`+digit does. */
    fun setFavourite(index: Int) {
        if (index !in favourites.indices) return
        favourites = favourites.toMutableList().also { it[index] = ink.colour }
        settings.colourFavourites = favourites
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
                ink = ink.withCustom(colour)
                settings.paletteCustom = ink.custom
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

    /** Close the active tab, activating its neighbour. */
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
    fun onKey(event: KeyEvent, onOpenPicker: () -> Unit): Boolean {
        // A held Space is a modifier, not a command: it starts on the press and ends on the release.
        if (event.key == Key.Spacebar && event.type == KeyEventType.KeyUp) {
            panMode = false
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        // A colour panel that is up owns `Esc`: it closes before anything else the key might mean.
        if (event.key == Key.Escape && editing != null) {
            cancelColourEdit()
            return true
        }
        // The order is the table's: a `Ctrl` command first (`Ctrl+1` stays fit width), then the ink keys
        // (digits and brackets, none of which is a tool's letter), then the tools.
        val command = shellCommand(event.key, event.isCtrlPressed, event.isShiftPressed)
        if (command != null) return apply(command, onOpenPicker)
        inkCommand(event.key, alt = event.isAltPressed, ctrl = event.isCtrlPressed)?.let { pressed ->
            when (pressed) {
                is InkCommand.PickFavourite -> pickFavourite(pressed.index)
                is InkCommand.SetFavourite -> setFavourite(pressed.index)
                is InkCommand.StepWidth -> stepWidth(pressed.direction)
            }
            return true
        }
        toolForKey(event.key, shift = event.isShiftPressed, ctrl = event.isCtrlPressed)?.let { tool ->
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
    fun apply(command: ShellCommand, onOpenPicker: () -> Unit = {}): Boolean {
        when (command) {
            ShellCommand.DISMISS_FAILURE -> {
                if (failure == null) return false
                failure = null
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
            else -> Unit
        }
        val tab = tabs.active ?: return false
        return when (command) {
            ShellCommand.CLOSE_TAB -> {
                closeActive(); true
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
            // Handled above, with no document needed.
            ShellCommand.DISMISS_FAILURE, ShellCommand.OPEN, ShellCommand.TOGGLE_SIDEBAR -> false
        }
    }
}

/**
 * The shell as the drawing in `docs/ui-modernization.md` lays it out: a header carrying the tab strip,
 * a sidebar in a lane of its own beside the canvas, the page on the desk, and the status bar under it.
 *
 * The bands are **glass, and the page is not underneath them**: the header and the status bar take their
 * own rows and the canvas takes the rectangle that is left, so no band ever lies on the sheet
 * (`docs/chrome.md`). Everything that floats — the failed-open card — may sit over the page, because it
 * comes and goes with a gesture.
 */
/**
 * The colour panel, held for its exit.
 *
 * It is a transient surface: it may lie over the page (a band may not), and it is centred so the strip
 * that opened it stays visible. [GlassPresence] keeps it composed while its spring draws back — the
 * shell clears `editing` the instant Set or Cancel is pressed, and without the hold the panel would
 * vanish between two frames — and the target is remembered for the same reason: the closing surface
 * still has to know which colour it was editing.
 */
@Composable
private fun BoxScope.ColourPanelHost(state: DesktopShellState) {
    var lastEdit by remember { mutableStateOf<ColourEditorTarget?>(null) }
    val editing = state.editing
    SideEffect { if (editing != null) lastEdit = editing }
    GlassPresence(visible = editing != null) {
        val target = editing ?: lastEdit ?: return@GlassPresence
        ColourPickerPanel(
            title = if (target == ColourEditorTarget.CUSTOM) "Custom colour" else "New colour",
            initial = if (target == ColourEditorTarget.CUSTOM) {
                state.ink.custom ?: state.ink.colour
            } else {
                state.ink.colour
            },
            onSet = { colour -> state.commitColour(colour) },
            onCancel = { state.cancelColourEdit() },
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun DesktopShell(state: DesktopShellState, onOpenPicker: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(DesktopTheme.desk)) {
        Header(
            tabs = state.tabs,
            sidebarVisible = state.sidebarVisible,
            placement = state.toolPlacement,
            onActivate = { index -> state.tabs.activate(index) },
            onClose = { index -> state.tabs.close(index) },
            onMove = { from, to -> state.tabs.move(from, to) },
            onOpen = onOpenPicker,
            onToggleSidebar = { state.toggleSidebar() },
            onPlacement = state::chooseToolPlacement,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val tab = state.active
            when {
                // With nothing open the notice owns the window; with a document open the card floats
                // over it instead, so an unopenable file cannot hide the one you were reading.
                tab == null && state.failure != null -> Notice(state.failure!!.title, state.failure!!.detail)
                tab == null -> Notice(
                    "Open a .xopp or .mobix file to start",
                    "Ctrl+T, or “Open…”. The preview shows paper, ink, living entities and PDF or " +
                        "image backgrounds, read-only.",
                )
                // The tool strips are bands in their own lanes: the ribbon takes a row under the
                // header, the rail a column beside the canvas, and the page keeps the rectangle that
                // is left. Neither is ever laid over the sheet (`docs/chrome.md`).
                else -> Column(Modifier.fillMaxSize()) {
                    if (state.toolPlacement.showsRibbon) {
                        ToolRibbon(
                            state = state.stripState,
                            onSelect = state::selectTool,
                            ink = InkActions(
                                onColour = state::pickColour,
                                onWidth = state::pickWidth,
                                onEditCustom = { state.editColour(ColourEditorTarget.CUSTOM) },
                                onAddColour = { state.editColour(ColourEditorTarget.NEW) },
                            ),
                        )
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (state.sidebarVisible) {
                            PageSidebar(
                                tab = tab,
                                cache = state.thumbnails.getOrPut(tab.key) { mutableMapOf() },
                                onSelectPage = { index -> selectPage(tab, index) },
                            )
                        }
                        if (state.toolPlacement.showsRail) {
                            ToolRail(
                                state = state.stripState,
                                onSelect = state::selectTool,
                                ink = InkActions(
                                    onColour = state::pickColour,
                                    onWidth = state::pickWidth,
                                    onEditCustom = { state.editColour(ColourEditorTarget.CUSTOM) },
                                    onAddColour = { state.editColour(ColourEditorTarget.NEW) },
                                ),
                            )
                        }
                        PageCanvas(
                            tab = tab,
                            panMode = state.panMode,
                            onViewport = { tab.viewport = it },
                            onFit = { tab.fit = it },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
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
        }
        StatusBar(state.active)
    }
}

@Composable
private fun Header(
    tabs: DesktopTabs,
    sidebarVisible: Boolean,
    placement: ToolPlacement,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onOpen: () -> Unit,
    onToggleSidebar: () -> Unit,
    onPlacement: (ToolPlacement) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopTheme.headerHeight)
            .bandSurface()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderGlyph(
            active = sidebarVisible,
            onClick = onToggleSidebar,
            draw = { color, stroke ->
                drawLine(color, androidx.compose.ui.geometry.Offset(0f, 2f), androidx.compose.ui.geometry.Offset(size.width, 2f), stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height / 2f), androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height - 2f), androidx.compose.ui.geometry.Offset(size.width, size.height - 2f), stroke)
            },
        )
        Spacer(Modifier.width(10.dp))
        TabStrip(
            tabs = tabs,
            onActivate = onActivate,
            onClose = onClose,
            onMove = onMove,
            onOpen = onOpen,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        PlacementButton(placement = placement, onPlacement = onPlacement)
    }
}

/**
 * The placement chooser: which of the two tool strips the window shows (Rail, Ribbon, Both).
 *
 * It is the one control of the Interface section the future settings window will own
 * (`desktop-settings-and-status-ui`), so until that window exists it stands in the header — and it
 * writes the same [DesktopSettings] entry the window will edit, so there is one setting with two doors
 * rather than two settings that can disagree.
 */
@Composable
private fun PlacementButton(placement: ToolPlacement, onPlacement: (ToolPlacement) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        HeaderGlyph(active = open, onClick = { open = !open }) { color, stroke ->
            // Three rows of different lengths: a rail, a ribbon, and both at once.
            drawLine(color, Offset(0f, 3f), Offset(size.width, 3f), stroke)
            drawLine(color, Offset(size.width * 0.45f, size.height / 2f), Offset(size.width, size.height / 2f), stroke)
            drawLine(color, Offset(0f, size.height - 3f), Offset(size.width, size.height - 3f), stroke)
        }
        if (open) {
            Popup(
                offset = with(density) { IntOffset(0, (DesktopTheme.headerHeight + 6.dp).roundToPx()) },
                onDismissRequest = { open = false },
            ) {
                Column(
                    Modifier
                        .glassSurface()
                        .padding(8.dp),
                ) {
                    Text("Tool placement", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
                    Spacer(Modifier.height(6.dp))
                    for (option in ToolPlacement.entries) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (option == placement) DesktopTheme.activeSlot else Color.Transparent,
                                    DesktopTheme.capsuleShape,
                                )
                                .pointerInput(option) {
                                    detectTapGestures { onPlacement(option); open = false }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(option.label, color = DesktopTheme.ink, style = DesktopTheme.body)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A header control: two or three monoline strokes on a **capsule of the header's own glass** — the
 * tablet's icon buttons beside the dock, at the smaller desktop size.
 *
 * It used to be a bare 26 dp box that only appeared when active: a glyph floating on the band with
 * nothing around it. The capsule is the family's smallest surface — the same fill as the band, the
 * family's rim and a 4 dp lift — so a control reads as an object lying on the header instead of as a
 * symbol printed on it (`docs/ui-modernization.md`).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HeaderGlyph(
    active: Boolean,
    onClick: () -> Unit,
    draw: DrawScope.(Color, Float) -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val lit = active || hovered
    Box(
        Modifier
            .size(DesktopTheme.headerControlSize)
            .glassCapsule(
                cornerRadius = DesktopTheme.headerControlSize / 2,
                fill = if (lit) DesktopTheme.activeSlot else DesktopTheme.glass,
                shadow = DesktopTheme.bandShadow,
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .pointerInput(active) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.width(14.dp).height(14.dp)) {
            // The chrome ink, lit or not: on the tablet a header glyph is white and only its capsule
            // changes tone when it is active.
            draw(DesktopTheme.chromeInk, DesktopTheme.glyphStroke.toPx())
        }
    }
}

/** The bottom bar: page, zoom, container and how far through the document the page is. */
@Composable
private fun StatusBar(tab: DesktopTab?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopTheme.statusHeight)
            .bandSurface()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab == null) {
            Text("no document", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            return@Row
        }
        val zoom = tab.viewport.zoom
        // The label names the fit that is *in use*: `AUTO` is a question (is this page a sheet or a
        // strip?), and a status bar that answered it differently from the canvas would be lying about
        // what its own zoom readout means. The page is already in the reader's cache either way.
        val page = remember(tab, tab.page) { runCatching { tab.document.page(tab.page) }.getOrNull() }
        val fit = page?.let { tab.fitFor(it.width.toFloat(), it.height.toFloat()) } ?: tab.fit
        Text(pageLabel(tab.page, tab.pageCount), color = DesktopTheme.inkSecondary, style = DesktopTheme.numeric)
        Spacer(Modifier.width(14.dp))
        Text(zoomLabel(zoom), color = DesktopTheme.inkSecondary, style = DesktopTheme.numeric)
        Spacer(Modifier.width(14.dp))
        Text(
            text = when (fit) {
                FitMode.NONE -> "free zoom"
                FitMode.WIDTH -> "fit width"
                FitMode.PAGE, FitMode.AUTO -> "fit page"
            },
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${tab.document.container.label} · ${tab.file.name}",
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        ProgressBar(pageProgress(tab.page, tab.pageCount))
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.width(90.dp).height(3.dp).background(DesktopTheme.activeSlot)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(DesktopTheme.accent),
        )
    }
}

@Composable
private fun Notice(title: String, detail: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = DesktopTheme.ink, style = DesktopTheme.title)
            Spacer(Modifier.height(8.dp))
            Text(detail, color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        }
    }
}

