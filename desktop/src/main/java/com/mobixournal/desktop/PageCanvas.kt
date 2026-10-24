package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import com.mobixournal.format.model.Page
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The page workspace: one page, zoomed and panned, on the desk.
 *
 * One page at a time rather than a scrolling column is a deliberate choice for a workstation: at 300 %
 * a document is a column of twenty screens, and "which page am I on" stops being answerable by
 * scrolling. The page you are on is [DesktopTab.page], the sidebar and the status bar both read it, and
 * the viewport (zoom, pan, fit) belongs to the tab, so switching tabs restores what each was showing.
 *
 * A page written as a **long strip** — a lecture note the tablet app saved as one 850 × 2976 pt sheet —
 * opens fitted to its **width** rather than shrunk to a sliver (`FitMode.AUTO`), and the wheel reads down
 * it. Scrolling therefore never abandons the fit: a width-fitted page must stay fitted while you travel
 * through it, or the first notch would turn the view into a free zoom and a window resize would stop
 * refitting the page.
 *
 * Input, in the conventions a document viewer has already taught everyone:
 *  - **wheel** scrolls (vertically, and horizontally for a wide page); **Ctrl + wheel** zooms at the
 *    cursor, which is the *page point under the cursor* staying still, not the page's centre;
 *  - the tablet's **dial** zooms at the cursor with no modifier held — its notches arrive as wheel
 *    notches from the tablet's own device, which is how they are told apart from the mouse's;
 *  - **middle-drag** or **space-drag** pans;
 *  - a **primary drag with the pen or highlighter** draws, and with the eraser rubs out — the gesture
 *    is decided by the shared `InkPointerRouter` (`PageCanvasInput.kt`), so the window has no second
 *    opinion about what a press means or how hard it draws;
 *  - the page is clamped into the window by [Viewport], so it cannot be dragged out of sight.
 *
 * A stroke is **previewed as it is drawn** and committed on lift as one edit through
 * [DesktopDocument.editPage]: the page the painter shows is the page the tab will save. A press that
 * lands off the sheet starts nothing, because the desk is not paper.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PageCanvas(
    tab: DesktopTab,
    panMode: Boolean,
    onViewport: (Viewport) -> Unit,
    onFit: (FitMode) -> Unit,
    tool: DesktopTool = DesktopTool.PEN,
    ink: DesktopInk = DesktopInk(),
    onEdited: (Int) -> Unit = {},
    /**
     * The window's pen stream, when it has one — Wintab's packets or Windows Ink's samples: the pen's
     * pressure, tilt and eraser end are folded into the pointer events this canvas already receives, so
     * a stroke is still one stroke.
     */
    pen: PenContactStream? = null,
    /**
     * The tablet's own packets, when the host has them: a stroke is drawn from them rather than from the
     * mouse Windows emulates the same contact as (`PenPacketStroke`).
     */
    packets: PenPacketSource? = null,
    /**
     * A diagnostics sink for the ink-latency probe (`InkProbe`): called after each paint with the
     * drafts that were drawn and the clock at that paint. Null in the editor, where this is one branch
     * per frame and no state.
     */
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)? = null,
    /**
     * True when the wheel notch Compose is delivering right now came from the tablet's **dial** rather
     * than the mouse. Windows sends both as `WM_MOUSEWHEEL` with the same ±120 delta and no modifier,
     * so the notch's *device* is what decides (`TabletWheel`): the dial's turns zoom at the cursor, and
     * the mouse's wheel keeps scrolling. One notch, one action — the raw report is a reading and the
     * Compose event is still the only thing that moves the page.
     */
    dialWheel: () -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    // Read once here: the gesture scope and the packet path both need it, and neither is in a draw scope.
    val density = LocalDensity.current.density
    val page = rememberedPage(tab)
    val loaded = page.getOrNull()
    // The strokes in progress, in page pt — the Compose pointer's and the tablet's, one draft each.
    val draft = remember(tab, tab.page) { mutableStateListOf<InkSample>() }
    val penDraft = remember(tab, tab.page) { mutableStateListOf<InkSample>() }
    // Where this canvas sits in the window, so a packet in client pixels becomes a point inside it.
    var origin by remember { mutableStateOf(Offset.Zero) }
    var extent by remember { mutableStateOf(Size.Zero) }
    // True while the tablet's packets own the pen: the mouse path stands aside for that stroke.
    val penOwns = remember(tab, tab.page) { mutableStateOf(false) }
    // Where the ring sits, and when the **driver** last said where the pen is. Both are lifted here because
    // two paths write them: the packets, and the Compose moves of a window with no stream.
    val hover = remember { mutableStateOf<Offset?>(null) }
    val penAtNanos = remember { mutableStateOf(0L) }
    val sheetPicture = rememberSheetPicture(tab)

    BoxWithConstraints(
        modifier
            .background(DESK)
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                origin = coordinates.boundsInWindow().topLeft
                extent = coordinates.size.toSize()
            },
    ) {
        val viewW = maxWidth.value
        val viewH = maxHeight.value
        if (loaded == null) {
            PageReadFailure(page.exceptionOrNull()?.message)
            return@BoxWithConstraints
        }

        PageSheet(
            tab = tab,
            page = loaded,
            sheet = sheetLayout(tab, loaded, viewW, viewH),
            tool = tool,
            ink = ink,
            draft = draft,
            penDraft = penDraft,
            pen = pen,
            penOwns = penOwns,
            hover = hover,
            penAtNanos = penAtNanos,
            sheetPicture = sheetPicture,
            paintProbe = paintProbe,
            measurer = measurer,
            density = density,
            panMode = panMode,
            dialWheel = dialWheel,
            origin = { origin },
            extent = { extent },
            onViewport = onViewport,
            onFit = onFit,
            onEdited = onEdited,
            packets = packets,
        )
    }
}

/** The page the tab is on, re-read when the document is edited; a failure is why it could not be read. */
@Composable
private fun rememberedPage(tab: DesktopTab): Result<Page> =
    remember(tab, tab.page, tab.document.edits) { runCatching { tab.document.page(tab.page) } }

/**
 * The page's recorded picture, released with the page it belongs to: it holds native drawing
 * instructions, so a page outliving its picture would leak them.
 */
@Composable
private fun rememberSheetPicture(tab: DesktopTab): PagePicture {
    val picture = remember(tab, tab.page) { PagePicture() }
    DisposableEffect(picture) { onDispose { picture.close() } }
    return picture
}

/**
 * The page inside the box the canvas measured: the tablet's packets first (they draw their own stroke),
 * then the sheet's canvas, which paints the page and takes the Compose pointer.
 *
 * Split out of [PageCanvas] rather than inlined: the canvas keeps the state a page needs, this keeps the
 * two children that spend it, which is the hundred-line rule `SourceStructureTest` enforces.
 */
@Composable
private fun PageSheet(
    tab: DesktopTab,
    page: Page,
    sheet: SheetLayout,
    tool: DesktopTool,
    ink: DesktopInk,
    draft: SnapshotStateList<InkSample>,
    penDraft: SnapshotStateList<InkSample>,
    pen: PenContactStream?,
    penOwns: MutableState<Boolean>,
    hover: MutableState<Offset?>,
    penAtNanos: MutableState<Long>,
    sheetPicture: PagePicture,
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)?,
    measurer: TextMeasurer,
    density: Float,
    panMode: Boolean,
    dialWheel: () -> Boolean,
    origin: () -> Offset,
    extent: () -> Size,
    onViewport: (Viewport) -> Unit,
    onFit: (FitMode) -> Unit,
    onEdited: (Int) -> Unit,
    packets: PenPacketSource?,
) {
    PenPacketStrokes(
        tab = tab,
        tool = tool,
        ink = ink,
        density = density,
        pageW = sheet.pageW,
        pageH = sheet.pageH,
        viewport = sheet.viewport,
        draft = penDraft,
        onEdited = onEdited,
        origin = origin,
        extent = extent,
        owns = penOwns,
        hover = hover,
        penAtNanos = penAtNanos,
        packets = packets,
    )
    SheetCanvas(
        tab = tab,
        page = page,
        sheet = sheet,
        tool = tool,
        ink = ink,
        draft = draft,
        penDraft = penDraft,
        pen = pen,
        penOwns = penOwns,
        hover = hover,
        penAtNanos = penAtNanos,
        sheetPicture = sheetPicture,
        paintProbe = paintProbe,
        measurer = measurer,
        density = density,
        panMode = panMode,
        dialWheel = dialWheel,
        onViewport = onViewport,
        onFit = onFit,
        onEdited = onEdited,
    )
}

/** A page's size and the viewport in force for it, in the window it is being shown in. */
private data class SheetLayout(
    val pageW: Float,
    val pageH: Float,
    val zoom: Float,
    val viewW: Float,
    val viewH: Float,
    val viewport: Viewport,
)

/**
 * The page's box and the zoom that fits it — and the one write the canvas makes to the tab's own
 * viewport.
 *
 * The status bar reports the *effective* zoom, which for a fitted page is the window's answer and not the
 * tab's stored one: publishing it keeps the readout true without a second source of truth. It happens
 * **only while a fit mode is on** — publishing it unconditionally is a stale-write race, where a zoom the
 * user asked for is overwritten by the fitted value of the *previous* frame.
 */
@Composable
private fun sheetLayout(tab: DesktopTab, page: Page, viewW: Float, viewH: Float): SheetLayout {
    val pageW = page.width.toFloat()
    val pageH = page.height.toFloat()
    val zoom = tab.fit.zoomFor(pageW, pageH, viewW, viewH) ?: tab.viewport.zoom
    // A fitted page is re-centred every layout pass; a free one keeps its own offsets, clamped.
    val viewport = tab.viewport.copy(zoom = zoom).clamped(pageW, pageH, viewW, viewH)
    LaunchedEffect(zoom, viewW, viewH, tab.fit) {
        if (tab.fit != FitMode.NONE && tab.viewport.zoom != zoom) {
            tab.viewport = tab.viewport.copy(zoom = zoom)
        }
    }
    return SheetLayout(pageW, pageH, zoom, viewW, viewH, viewport)
}

/**
 * The page on screen: the scroll, the pointer gestures, and the painted sheet.
 *
 * Split out of [PageCanvas] so the composable that owns a page is layout and wiring, and this one is the
 * surface (`SourceStructureTest` caps a function's length).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SheetCanvas(
    tab: DesktopTab,
    page: Page,
    sheet: SheetLayout,
    tool: DesktopTool,
    ink: DesktopInk,
    draft: SnapshotStateList<InkSample>,
    /** The tablet's stroke, drawn from its packets — a draft of its own, painted beside [draft]. */
    penDraft: SnapshotStateList<InkSample>,
    pen: PenContactStream?,
    penOwns: MutableState<Boolean>,
    /** The ring's position — written by the packets while the pen is in range, by the mouse otherwise. */
    hover: MutableState<Offset?>,
    /** `nanoTime` of the newest driver reading; a mouse move is ignored while this is fresh. */
    penAtNanos: MutableState<Long>,
    /** The page's recorded picture, so a frame replays the sheet instead of re-painting it. */
    sheetPicture: PagePicture,
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)? = null,
    measurer: TextMeasurer,
    density: Float,
    panMode: Boolean,
    dialWheel: () -> Boolean,
    onViewport: (Viewport) -> Unit,
    onFit: (FitMode) -> Unit,
    onEdited: (Int) -> Unit,
) {
    // The pen's pointer: a ring the size of the tool instead of the desktop's arrow (`DesktopCursor.kt`).
    // Its position is [hover], lifted into `PageCanvas` so the driver's own readings can move it.
    val ringPx = toolCursorDiameterPx(tool, ink.width, sheet.zoom, density)
    val penIcon = rememberBlankPointerIcon()
    // A mouse move that is a *copy* of the contact must not drag the ring back to a position the driver
    // has already left behind; the packets win for as long as they are arriving.
    val followMouse = { System.nanoTime() - penAtNanos.value > PenPacketStroke.HOVER_STALE_NANOS }

    Canvas(
        Modifier.fillMaxSize()
            .pointerHoverIcon(if (ringPx != null) penIcon else PointerIcon.Default)
            .onPointerEvent(PointerEventType.Enter) { event ->
                if (followMouse()) hover.value = event.changes.firstOrNull()?.position
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                if (followMouse()) hover.value = event.changes.firstOrNull()?.position
            }
            .onPointerEvent(PointerEventType.Exit) { if (followMouse()) hover.value = null }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                wheelViewport(
                    event, sheet.viewport, sheet.zoom, density, sheet.pageW, sheet.pageH,
                    sheet.viewW, sheet.viewH, onFit,
                    forceZoom = dialWheel(),
                )?.let(onViewport)
            }
            .pointerInput(tab, panMode, tool, ink, pen, sheet) {
                pageGestures(
                    tab = tab,
                    panMode = panMode,
                    tool = tool,
                    ink = ink,
                    viewport = sheet.viewport,
                    pageW = sheet.pageW,
                    pageH = sheet.pageH,
                    viewW = sheet.viewW,
                    viewH = sheet.viewH,
                    draft = draft,
                    pen = pen,
                    penActive = { penOwns.value },
                    onViewport = onViewport,
                    onEdited = onEdited,
                )
            },
    ) {
        paintSheet(
            sheetPicture = sheetPicture,
            page = page,
            viewport = sheet.viewport,
            zoom = sheet.zoom,
            density = density,
            measurer = measurer,
            backdrop = tab.document.backdrop,
            hiddenLayers = tab.hiddenOn(tab.page),
            tool = tool,
            ink = ink,
            drafts = listOf(draft.toList(), penDraft.toList()),
            paintProbe = paintProbe,
        )
        // Above the sheet, in the canvas's pixels: the ring marks the point, not the page.
        val ring = ringPx
        val at = hover.value
        if (ring != null && at != null) drawToolCursor(at, ring)
    }
}

/**
 * The sheet and the stroke being drawn, in the **page's own** coordinate space, carried by [viewport]:
 * paper, picture and ink scale together, which is what makes a zoomed stroke keep the width it was
 * drawn with.
 *
 * Split out of the composable so the canvas keeps its layout, its scroll and its gestures
 * (`SourceStructureTest` caps a function's length) — and because the preview is painted with the same
 * rule as the committed ink: a preview that looked different from what the lift writes would be a lie
 * about the file.
 */
private fun DrawScope.paintSheet(
    /** The recorded page — replayed by every frame, recorded again only when something changed. */
    sheetPicture: PagePicture,
    page: Page,
    viewport: Viewport,
    zoom: Float,
    density: Float,
    measurer: TextMeasurer,
    backdrop: PageBackdrop?,
    hiddenLayers: Set<Int>,
    tool: DesktopTool,
    ink: DesktopInk,
    /**
     * The strokes in progress, one list per input path.
     *
     * Two, not one: the tablet's packets and the Compose pointer each own their draft, so a cancelled
     * mouse gesture can never erase the samples of a stroke the pen is still drawing. They are painted as
     * separate previews because they are separate strokes until one of them is committed.
     */
    drafts: List<List<InkSample>>,
    /** The ink probe's sink; null in the editor. Called after the ink, at the same clock reading. */
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)? = null,
) {
    // Resolved before the transform: inside it the receiver is a DrawTransform, which has no display
    // density of its own.
    val startedAt = System.nanoTime()
    val pxPerPt = zoom * density
    // The sheet: recorded once per viewport, replayed per frame (see `PagePicture`). Recording is the
    // Kotlin cost — `LivingInk`, text measurement, a `Path` per stroke — and a frame must not pay it.
    sheetPicture.draw(this, page, viewport, zoom, density, backdrop, hiddenLayers, measurer)
    withTransform({ translate(viewport.offsetX * density, viewport.offsetY * density) }) {
        val inkTool = tool.inkTool()
        if (inkTool == null) return@withTransform
        for (draft in drafts) {
            if (draft.isEmpty()) continue
            strokeOf(inkTool, ink.colour, ink.width, draft)?.let { draftStroke ->
                drawInkStroke(draftStroke, pxPerPt)
            }
        }
    }
    // The probe times the paint itself, after the ink was laid down: that is the frame the eye saw.
    paintProbe?.invoke(drafts, System.nanoTime())
    // The watchdog counts every repaint, whichever path caused it — a window that paints while nothing is
    // happening is the failure it exists to catch — and times it, which is what the frame budget is.
    DesktopDiagnostics.painted((System.nanoTime() - startedAt) / 1_000_000.0)
}

/**
 * The tablet's packets, drawing this canvas's stroke.
 *
 * The gesture is the canvas's **own**, not the one `pageGestures` builds per pointer gesture: a packet
 * stream has no beginning and no end to wrap, it simply stops when the pen lifts. Everything else is the
 * shared path — `PenPacketStroke` converts, `PageInkGesture` routes, the router and the pressure filter
 * decide — and this only keeps the subscription tied to the canvas's lifetime, plus the tick that finishes a
 * stroke whose packets stop arriving.
 *
 * [draft] is the pen's **own** list: the Compose pointer keeps [PageCanvas]'s other draft, so the emulated
 * mouse neither begins, extends nor ends this stroke.
 */
@Composable
private fun PenPacketStrokes(
    tab: DesktopTab,
    tool: DesktopTool,
    ink: DesktopInk,
    density: Float,
    pageW: Float,
    pageH: Float,
    viewport: Viewport,
    draft: SnapshotStateList<InkSample>,
    onEdited: (Int) -> Unit,
    origin: () -> Offset,
    extent: () -> Size,
    owns: MutableState<Boolean>,
    /** Where the driver last put the pen, in this canvas's pixels — the ring follows it. */
    hover: MutableState<Offset?>,
    /** When that reading arrived, so a mouse move can tell whether the driver is still talking. */
    penAtNanos: MutableState<Long>,
    packets: PenPacketSource?,
) {
    // Read live: a packet can arrive after a zoom, and it must map to the page the reader is looking at.
    val liveViewport by rememberUpdatedState(viewport)
    val gesture = remember(tab, tool, ink, pageW, pageH) {
        // `startOnDown = true`: a pen on the glass starts its stroke at the press, so a touch that never
        // travels is a dot and a very light stroke is not mistaken for a click.
        PageInkGesture(
            tab = tab,
            tool = tool,
            ink = ink,
            view = { liveViewport },
            pageW = pageW,
            pageH = pageH,
            density = density,
            draft = draft,
            onEdited = onEdited,
            startOnDown = true,
        )
    }
    // One ring update per frame, not one per packet: the driver sends 400 readings a second and each
    // write to [hover] repaints the canvas.
    val showAt: (Offset?) -> Unit = { at ->
        if (hover.value != at) hover.value = at
        penAtNanos.value = System.nanoTime()
    }
    val stroke = remember(gesture) {
        PenPacketStroke(
            gesture = gesture,
            origin = origin,
            size = extent,
            onOwnership = { owns.value = it },
            onHover = showAt,
        )
    }
    DisposableEffect(packets, stroke) {
        val stop = packets?.listen(stroke::packet)
        onDispose { stop?.invoke() }
    }
    // One publish per frame: the packets buffered since the last repaint are handed to the gesture
    // here, so 400 packets a second cost one invalidation per frame instead of 400 — and the same
    // point also notices a pen lifted without its last packet, which must not leave a stroke half drawn.
    LaunchedEffect(stroke) {
        // The silence that ends a stroke is *absence*, and absence produces no packets to wait for — so it
        // is a timer, not a frame. It runs on `delay`, which asks for no repaint at all.
        launch {
            while (true) {
                delay(PenPacketStroke.STALE_CHECK_MS)
                stroke.finishIfStale(System.nanoTime())
            }
        }
        // Frames are **asked for by packets**, never by a loop: a `withFrameNanos` loop that never ends
        // renders the window at the display's full rate for ever — measured at ~120 fps on a window with
        // nothing happening, which on a 21:9 sheet is what starved the message thread and read as the
        // window freezing. One token per batch means the ink still costs at most one frame per screen.
        //
        // The batch is applied **here**, not after the next frame (`withFrameNanos { flush() }` used to
        // sit between the two): a resumed frame awaiter is dispatched back to this thread *after* that
        // frame has been drawn, so waiting for it spent a frame and the ink landed on the one following.
        // Measured on a 120 Hz display: 12.45 ms median / 16.82 ms p95, which is two 8.33 ms frames.
        // Writing the samples now invalidates the canvas, and Compose paints it at the very next frame.
        while (true) {
            stroke.awaitPacket()
            stroke.flush()
            stroke.publishHover()
        }
    }
}

/**
 * What a page that cannot be read says — the same sentence in the same place, so a broken page is one
 * line about the page and not a stack of chrome around it.
 */
@Composable
private fun BoxScope.PageReadFailure(reason: String?) {
    Text(
        text = "Could not read this page: ${reason ?: "no reason given"}",
        color = DesktopTheme.danger,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.align(Alignment.Center),
    )
}

/** The desk behind the page: true black, as decided in `docs/ui-modernization.md`. */
private val DESK = DesktopTheme.desk

/** A page's on-screen size in dp at [zoom] — used by the sidebar's progress bar and the status bar. */
internal fun pageSizeDp(pageW: Double, pageH: Double, zoom: Float): Pair<Float, Float> =
    (pageW * zoom).toFloat() to (pageH * zoom).toFloat()

/** The zoom label a status bar shows. */
internal fun zoomLabel(zoom: Float): String = "${(zoom * 100f).roundToInt()}%"

/** A page's label: `Page 3 / 12`. */
internal fun pageLabel(page: Int, count: Int): String = "Page ${page + 1} / ${count.coerceAtLeast(1)}"
