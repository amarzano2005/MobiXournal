package com.mobixournal.desktop

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.FileDialog
import java.io.File

/**
 * The desktop shell: open documents as tabs, navigate them with a sidebar, zoom and pan the page.
 *
 * Every word the window shows is **English**, like the tablet app's UI (`docs/ui-modernization.md`
 * owns the rule): the desktop is the same product, not a translated companion.
 *
 * The layout is the workstation one from `docs/ui-modernization.md` — a header carrying the tab strip,
 * a sidebar beside the canvas (never over it), the page on a true-black desk, and a status bar with the
 * page and the zoom. Everything the chrome does has a key: `Ctrl+T` open, `Ctrl+W` close, `Ctrl+Tab`
 * switch, `Ctrl+B` sidebar, `Ctrl+=`/`Ctrl+-`/`Ctrl+0` zoom, `PgUp`/`PgDn` page.
 *
 * The view state lives in [DesktopTabs], one [DesktopTab] per document: a tab keeps its own page, zoom
 * and fit, so switching documents returns you to what you were looking at.
 */
@Composable
fun FrameWindowScope.DesktopApp(initial: File? = null) {
    val tabs = remember { DesktopTabs() }
    var active by remember { mutableStateOf<DesktopTab?>(null) }
    var failure by remember { mutableStateOf<OpenFailure?>(null) }
    var sidebarVisible by remember { mutableStateOf(true) }
    var panMode by remember { mutableStateOf(false) }
    val thumbnails = remember { mutableMapOf<String, MutableMap<Int, androidx.compose.ui.graphics.ImageBitmap>>() }
    val focus = remember { FocusRequester() }

    fun open(file: File) {
        runCatching { DocumentLoader.load(file) }
            .onSuccess { document ->
                active = tabs.open(document)
                failure = null
            }
            // A failed open is reported as itself, and never takes the document already on screen with it.
            .onFailure { failure = describeOpenFailure(it) }
    }

    fun pick() {
        val dialog = FileDialog(window, "Open a document", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            name.endsWith(".xopp", ignoreCase = true) || name.endsWith(".mobix", ignoreCase = true)
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) open(File(dir, name))
    }

    fun closeActive() {
        tabs.closeActive()
        active = tabs.active
    }

    LaunchedEffect(initial) { initial?.let(::open) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) { onDispose { tabs.closeAll() } }

    /** The window's keys: every one of them is also in the shortcuts table of `docs/ui-modernization.md`. */
    fun onKey(event: KeyEvent): Boolean {
        // Esc dismisses the failed-open card first, even though there may be no tab behind it.
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && failure != null) {
            failure = null
            return true
        }
        val tab = active ?: return false
        if (event.type == KeyEventType.KeyUp && event.key == Key.Spacebar) {
            panMode = false
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        val ctrl = event.isCtrlPressed
        return when {
            ctrl && event.key == Key.T -> {
                pick(); true
            }
            ctrl && event.key == Key.W -> {
                closeActive(); true
            }
            ctrl && event.key == Key.Tab -> {
                active = if (event.isShiftPressed) tabs.previous() else tabs.next(); true
            }
            ctrl && event.key == Key.B -> {
                sidebarVisible = !sidebarVisible; true
            }
            ctrl && isZoomIn(event.key, event.isShiftPressed) -> {
                zoomBy(tab, ZOOM_STEP); true
            }
            ctrl && isZoomOut(event.key) -> {
                zoomBy(tab, 1f / ZOOM_STEP); true
            }
            ctrl && event.key == Key.Zero -> {
                tab.fit = FitMode.PAGE; tab.viewport = tab.viewport.copy(offsetX = 0f, offsetY = 0f); true
            }
            ctrl && event.key == Key.One -> {
                tab.fit = FitMode.WIDTH; tab.viewport = tab.viewport.copy(offsetX = 0f, offsetY = 0f); true
            }
            event.key == Key.PageDown || event.key == Key.DirectionDown && !ctrl -> {
                selectPage(tab, (tab.page + 1).coerceAtMost(tab.pageCount - 1)); true
            }
            event.key == Key.PageUp || event.key == Key.DirectionUp && !ctrl -> {
                selectPage(tab, (tab.page - 1).coerceAtLeast(0)); true
            }
            event.key == Key.Spacebar -> {
                panMode = true; true
            }
            else -> false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(DESK)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent(::onKey),
        ) {
            Header(
                tabs = tabs,
                onActivate = { index -> active = tabs.activate(index) },
                onClose = { index ->
                    tabs.close(index)
                    active = tabs.active
                },
                onMove = { from, to ->
                    tabs.move(from, to)
                    active = tabs.active
                },
                onOpen = ::pick,
                sidebarVisible = sidebarVisible,
                onToggleSidebar = { sidebarVisible = !sidebarVisible },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val tab = active
                when {
                    // With nothing open the notice owns the window; with a document open the card floats
                    // over it (below) instead, so an unopenable file cannot hide the one you were reading.
                    tab == null && failure != null -> Notice(failure!!.title, failure!!.detail)
                    tab == null -> Notice(
                        "Open a .xopp or .mobix file to start",
                        "Ctrl+T, or “Open…”. The preview shows paper, ink, living entities and PDF or " +
                            "image backgrounds, read-only.",
                    )
                    else -> Row(Modifier.fillMaxSize()) {
                        if (sidebarVisible) {
                            PageSidebar(
                                tab = tab,
                                cache = thumbnails.getOrPut(tab.key) { mutableMapOf() },
                                onSelectPage = { index -> selectPage(tab, index) },
                            )
                        }
                        PageCanvas(
                            tab = tab,
                            panMode = panMode,
                            onViewport = { tab.viewport = it },
                            onFit = { tab.fit = it },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                if (tab != null) {
                    failure?.let { reported ->
                        OpenFailureCard(
                            failure = reported,
                            onDismiss = { failure = null },
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                        )
                    }
                }
            }
            StatusBar(active)
        }
    }
}

@Composable
private fun Header(
    tabs: DesktopTabs,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onOpen: () -> Unit,
    sidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(HEADER_HEIGHT).background(CHROME).padding(horizontal = 10.dp),
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

@Composable
private fun HeaderGlyph(
    active: Boolean,
    onClick: () -> Unit,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color, Float) -> Unit,
) {
    Box(
        Modifier
            .width(26.dp)
            .height(26.dp)
            .background(if (active) Color(0xFF2A2A31) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .pointerInput(active) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.width(12.dp).height(12.dp)) {
            draw(if (active) GLYPH_ACTIVE else GLYPH_IDLE, 1.4.dp.toPx())
        }
    }
}

/** The bottom bar: page, zoom, container and how far through the document the page is. */
@Composable
private fun StatusBar(tab: DesktopTab?) {
    Row(
        Modifier.fillMaxWidth().height(STATUS_HEIGHT).background(CHROME).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab == null) {
            Text("no document", color = Color(0xFF6E6E78), fontSize = 11.sp)
            return@Row
        }
        val zoom = tab.viewport.zoom
        // The label names the fit that is *in use*: `AUTO` is a question (is this page a sheet or a
        // strip?), and a status bar that answered it differently from the canvas would be lying about
        // what its own zoom readout means. The page is already in the reader's cache either way.
        val page = remember(tab, tab.page) { runCatching { tab.document.page(tab.page) }.getOrNull() }
        val fit = page?.let { tab.fitFor(it.width.toFloat(), it.height.toFloat()) } ?: tab.fit
        Text(
            text = pageLabel(tab.page, tab.pageCount),
            color = Color(0xFF9A9AA6),
            style = TabularStyle,
        )
        Spacer(Modifier.width(14.dp))
        Text(zoomLabel(zoom), color = Color(0xFF9A9AA6), style = TabularStyle)
        Spacer(Modifier.width(14.dp))
        Text(
            text = when (fit) {
                FitMode.NONE -> "free zoom"
                FitMode.WIDTH -> "fit width"
                FitMode.PAGE, FitMode.AUTO -> "fit page"
            },
            color = Color(0xFF6E6E78),
            fontSize = 11.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${tab.document.container.label} · ${tab.file.name}",
            color = Color(0xFF6E6E78),
            fontSize = 11.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        ProgressBar(pageProgress(tab.page, tab.pageCount))
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.width(90.dp).height(3.dp).background(Color(0xFF2A2A31))) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Color(0xFF60A5FA)),
        )
    }
}

@Composable
private fun Notice(title: String, detail: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(detail, color = Color(0xFF9A9AA6), style = MaterialTheme.typography.bodyMedium)
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

private val DESK = Color(0xFF000000)
private val CHROME = Color(0xFF141418)
private val GLYPH_ACTIVE = Color(0xFFE6E6EF)
private val GLYPH_IDLE = Color(0xFF9A9AA6)
/** Tabular figures: a page counter that changes width as it counts is a counter you notice. */
private val TabularStyle = TextStyle(fontSize = 11.sp, fontFeatureSettings = "tnum")

private val HEADER_HEIGHT = 44.dp
private val STATUS_HEIGHT = 24.dp

/** Zooms a tab around its centre (the keyboard path; the wheel zooms around the cursor). */
internal fun zoomBy(tab: DesktopTab, factor: Float) {
    tab.fit = FitMode.NONE
    tab.viewport = tab.viewport.copy(zoom = (tab.viewport.zoom * factor).coerceIn(Viewport.MIN_ZOOM, Viewport.MAX_ZOOM))
}

/** The label a document's tab gets before a rename, kept here so the strip and tests agree. */
internal fun tabLabel(file: File): String = file.name

@Suppress("unused")
private val keepFontWeight = FontWeight.SemiBold
