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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
class DesktopShellState {

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
        val command = shellCommand(event.key, event.isCtrlPressed, event.isShiftPressed) ?: return false
        return apply(command, onOpenPicker)
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

/** What one key press means, independent of the platform's event type (and therefore testable). */
enum class ShellCommand {
    OPEN,
    CLOSE_TAB,
    NEXT_TAB,
    PREVIOUS_TAB,
    TOGGLE_SIDEBAR,
    ZOOM_IN,
    ZOOM_OUT,
    FIT_PAGE,
    FIT_WIDTH,
    NEXT_PAGE,
    PREVIOUS_PAGE,
    START_PAN,
    DISMISS_FAILURE,
}

/**
 * The shortcut table of `docs/ui-modernization.md`, as a decision.
 *
 * Order matters in two places: `Ctrl + 0` is "fit page" **only** without Shift (with Shift it is the
 * zoom-in key on layouts where `=` shares the `0`), and the zoom keys are checked before it so the
 * more specific binding wins.
 */
internal fun shellCommand(key: Key, ctrl: Boolean, shift: Boolean): ShellCommand? = when {
    key == Key.Escape -> ShellCommand.DISMISS_FAILURE
    ctrl && key == Key.T -> ShellCommand.OPEN
    ctrl && key == Key.W -> ShellCommand.CLOSE_TAB
    ctrl && key == Key.Tab -> if (shift) ShellCommand.PREVIOUS_TAB else ShellCommand.NEXT_TAB
    ctrl && key == Key.B -> ShellCommand.TOGGLE_SIDEBAR
    ctrl && isZoomIn(key, shift) -> ShellCommand.ZOOM_IN
    ctrl && isZoomOut(key) -> ShellCommand.ZOOM_OUT
    ctrl && key == Key.Zero -> ShellCommand.FIT_PAGE
    ctrl && key == Key.One -> ShellCommand.FIT_WIDTH
    !ctrl && (key == Key.PageDown || key == Key.DirectionDown) -> ShellCommand.NEXT_PAGE
    !ctrl && (key == Key.PageUp || key == Key.DirectionUp) -> ShellCommand.PREVIOUS_PAGE
    key == Key.Spacebar -> ShellCommand.START_PAN
    else -> null
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
@Composable
fun DesktopShell(state: DesktopShellState, onOpenPicker: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(DesktopTheme.desk)) {
        Header(
            tabs = state.tabs,
            sidebarVisible = state.sidebarVisible,
            onActivate = { index -> state.tabs.activate(index) },
            onClose = { index -> state.tabs.close(index) },
            onMove = { from, to -> state.tabs.move(from, to) },
            onOpen = onOpenPicker,
            onToggleSidebar = { state.toggleSidebar() },
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
                else -> Row(Modifier.fillMaxSize()) {
                    if (state.sidebarVisible) {
                        PageSidebar(
                            tab = tab,
                            cache = state.thumbnails.getOrPut(tab.key) { mutableMapOf() },
                            onSelectPage = { index -> selectPage(tab, index) },
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
            if (tab != null) {
                state.failure?.let { reported ->
                    OpenFailureCard(
                        failure = reported,
                        onDismiss = { state.dismissFailure() },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    )
                }
            }
        }
        StatusBar(state.active)
    }
}

@Composable
private fun Header(
    tabs: DesktopTabs,
    sidebarVisible: Boolean,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onOpen: () -> Unit,
    onToggleSidebar: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopTheme.headerHeight)
            .background(DesktopTheme.glass)
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
    }
}

/** A header glyph: two or three monoline strokes, drawn at the token's width, never a filled icon. */
@Composable
private fun HeaderGlyph(
    active: Boolean,
    onClick: () -> Unit,
    draw: DrawScope.(Color, Float) -> Unit,
) {
    Box(
        Modifier
            .width(26.dp)
            .height(26.dp)
            .background(if (active) DesktopTheme.activeSlot else Color.Transparent, DesktopTheme.capsuleShape)
            .pointerInput(active) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.width(12.dp).height(12.dp)) {
            draw(if (active) DesktopTheme.ink else DesktopTheme.inkSecondary, DesktopTheme.glyphStroke.toPx())
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
            .background(DesktopTheme.glass)
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

/** Select a page, keeping the tab's fit mode and re-centring the free viewport on it. */
internal fun selectPage(tab: DesktopTab, index: Int) {
    tab.page = index.coerceIn(0, (tab.pageCount - 1).coerceAtLeast(0))
    tab.viewport = tab.viewport.copy(offsetX = 0f, offsetY = 0f)
}

/** One keyboard zoom step. */
internal const val ZOOM_STEP = 1.25f

/**
 * The keys that mean "zoom in" on the keyboard, whatever the layout puts on them.
 *
 * `Ctrl + =` is the documented shortcut, but `=` is not a key everywhere: on an Italian keyboard it is
 * **Shift + 0**, so the physical shortcut arrives as `Ctrl + Shift + 0` and a plain `Key.Equals` test
 * would never fire — worse, it would fall through to `Ctrl + 0` (fit page) and silently undo the zoom.
 * The keypad `+` is the layout-independent one. `Key.Plus` covers layouts where `+` is a key of its own.
 */
internal fun isZoomIn(key: Key, shift: Boolean): Boolean =
    key == Key.Equals ||
        key == Key.Plus ||
        key == Key.NumPadAdd ||
        (shift && key == Key.Zero)

/** The mirror of [isZoomIn]: `Ctrl + -` and the keypad's `-`. */
internal fun isZoomOut(key: Key): Boolean = key == Key.Minus || key == Key.NumPadSubtract

/** Zooms a tab around its centre (the keyboard path; the wheel zooms around the cursor). */
internal fun zoomBy(tab: DesktopTab, factor: Float) {
    tab.fit = FitMode.NONE
    tab.viewport = tab.viewport.copy(zoom = (tab.viewport.zoom * factor).coerceIn(Viewport.MIN_ZOOM, Viewport.MAX_ZOOM))
}

/** The label a document's tab gets before a rename, kept here so the strip and tests agree. */
internal fun tabLabel(file: File): String = file.name
