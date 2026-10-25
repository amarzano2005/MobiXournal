package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.render.Bounds
import com.mobixournal.render.CircuitElementFactory
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.ShapeKind
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    /**
     * The ink a **figure, a circuit or a placement** is written in: the pen's own, whatever tool is
     * picked. The tablet keeps figures on the pencil's colour even while the highlighter is in hand, so a
     * yellow marker never turns a rectangle yellow; a canvas on its own uses the picked tool's ink.
     */
    shapeInk: DesktopInk = ink,
    circuitKind: ShapeKind = LivingPlacement.DEFAULT_CIRCUIT_KIND,
    /**
     * What a lifted living-slot drag means. The window asks the family's values first ([DesktopShellState]'s
     * placement card); a canvas on its own commits the family's defaults, which is what a test drives.
     */
    onPlaceLiving: ((LivingSlot, Bounds) -> Unit)? = null,
    /** What a lifted **circuit** drag means: the part, and the two page points it spanned. */
    onPlaceCircuit: ((ShapeKind, CircuitSpan) -> Unit)? = null,
    /** The Text slot's box and callbacks; nothing is on the page until the shell commits it. */
    text: TextBoxUi = TextBoxUi(),
    /** What a double-click on the selected living element means: open its editor (the shell's pencil). */
    onEditLiving: (() -> Unit)? = null,
    onEdited: (Int) -> Unit = {},
    /** The window's pen stream (Wintab or Windows Ink), folded into the pointer events. */
    pen: PenContactStream? = null,
    /** The tablet's packets, when the host has them: a stroke is drawn from them (`PenPacketStroke`). */
    packets: PenPacketSource? = null,
    /**
     * A diagnostics sink for the ink-latency probe (`InkProbe`): the drafts drawn and the clock at that
     * paint. Null in the editor.
     */
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)? = null,
    /**
     * True when the notch came from the tablet's **dial** rather than the mouse: Windows sends both as
     * `WM_MOUSEWHEEL`, so the notch's device is what decides (`TabletWheel`) — the dial zooms.
     */
    dialWheel: () -> Boolean = { false },
    /** The drag in flight that `Esc` cancels, or null; the shell holds the other end. */
    onLiveDrag: ((() -> Unit)?) -> Unit = {},
    /** What the selection bar may offer; the default draws none (a canvas on its own has no clipboard). */
    selectionBar: SelectionBarUi = SelectionBarUi(),
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    // The one meaning a lifted living-slot drag has, chosen before the gesture runs.
    val placeLiving = onPlaceLiving ?: defaultPlacer(tab, ink, circuitKind, onEdited)
    // Read once here: the gesture scope and the packet path both need it, and neither is in a draw scope.
    val density = LocalDensity.current.density
    val page = rememberedPage(tab)
    // The strokes in progress and where the canvas sits in the window — the readings a packet needs.
    val memory = remember(tab, tab.page) { CanvasMemory() }
    val sheetPicture = rememberSheetPicture(tab)

    BoxWithConstraints(
        modifier
            .background(DESK)
            .clipToBounds()
            .onGloballyPositioned(memory::track),
    ) {
        CanvasBox(
            tab = tab,
            page = page,
            tool = tool,
            ink = ink,
            shapeInk = shapeInk,
            circuitKind = circuitKind,
            placeLiving = placeLiving,
            placeCircuit = onPlaceCircuit,
            text = text,
            onEditLiving = onEditLiving,
            draft = memory.draft,
            penDraft = memory.penDraft,
            pen = pen,
            pointer = memory.pointer,
            sheetPicture = sheetPicture,
            paintProbe = paintProbe,
            measurer = measurer,
            density = density,
            panMode = panMode,
            dialWheel = dialWheel,
            onLiveDrag = onLiveDrag,
            selectionBar = selectionBar,
            origin = { memory.origin },
            extent = { memory.extent },
            onViewport = onViewport,
            onFit = onFit,
            onEdited = onEdited,
            packets = packets,
        )
    }
}

/**
 * The pen's own state, lifted out of [PageCanvas]: whether the tablet's packets own the stroke in
 * progress, where the ring is, when the driver last spoke, and the vertical-space grab line.
 *
 * Two input paths write these (the packets, and the Compose moves of a window with no stream), which is
 * why they are remembered by the canvas rather than by the path that happens to run.
 */
internal class PenPointer {
    val owns = mutableStateOf(false)
    val hover = mutableStateOf<Offset?>(null)
    val atNanos = mutableStateOf(0L)
    val grabLine = mutableStateOf<Double?>(null)
    /** The region a selection drag is tracing right now: the sheet paints it, the gesture writes it. */
    val previewRegion = mutableStateOf<SelectionRegion?>(null)
    /**
     * The living element a placement drag is drawing right now — a component being pulled out along the
     * pen, shown exactly as it will land. Nothing of it is on the page until the lift.
     */
    val previewLiving = mutableStateOf<Element?>(null)
}

/**
 * The page state a canvas keeps across recompositions: the two strokes in progress and the pen's own
 * readings.
 *
 * Remembered **per tab and page**, so a page change starts clean, and lifted out of the composable because
 * the canvas is already the page's layout and wiring (`SourceStructureTest` counts both).
 */
private class CanvasMemory {
    /** The Compose pointer's stroke, in page pt. */
    val draft: SnapshotStateList<InkSample> = mutableStateListOf()

    /** The tablet's stroke, drawn from its packets — a draft of its own, painted beside [draft]. */
    val penDraft: SnapshotStateList<InkSample> = mutableStateListOf()

    val pointer = PenPointer()

    /**
     * Where this canvas sits in the window, in its client pixels — a packet arrives in the window's own
     * space and the canvas resolves it against this (`PenPacketStroke`). Kept here rather than as two
     * locals so both readings are written by one call and a canvas keeps them across recompositions.
     */
    var origin by mutableStateOf(Offset.Zero)
        private set
    var extent by mutableStateOf(Size.Zero)
        private set

    /** The canvas's box, as the layout reports it: its corner and its size. */
    fun track(coordinates: LayoutCoordinates) {
        origin = coordinates.boundsInWindow().topLeft
        extent = coordinates.size.toSize()
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
internal fun rememberSheetPicture(tab: DesktopTab): PagePicture {
    val picture = remember(tab, tab.page) { PagePicture() }
    DisposableEffect(picture) { onDispose { picture.close() } }
    return picture
}


/**
 * The ring follows the mouse — but only while the driver's packets are quiet, so a contact the driver has
 * already moved on from does not drag the ring back to where the emulated mouse still thinks it is.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.hoverTracking(pointer: PenPointer, followMouse: () -> Boolean): Modifier =
    onPointerEvent(PointerEventType.Enter) { event ->
        if (followMouse()) pointer.hover.value = event.changes.firstOrNull()?.position
    }
        .onPointerEvent(PointerEventType.Move) { event ->
            if (followMouse()) pointer.hover.value = event.changes.firstOrNull()?.position
        }
        .onPointerEvent(PointerEventType.Exit) { if (followMouse()) pointer.hover.value = null }

/** A page's size and the viewport in force for it, in the window it is being shown in. */
internal data class SheetLayout(
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
internal fun sheetLayout(tab: DesktopTab, page: Page, viewW: Float, viewH: Float): SheetLayout {
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
internal fun SheetCanvas(
    tab: DesktopTab,
    page: Page,
    sheet: SheetLayout,
    tool: DesktopTool,
    ink: DesktopInk,
    shapeInk: DesktopInk,
    circuitKind: ShapeKind,
    onPlaceLiving: (LivingSlot, Bounds) -> Unit,
    /** The shell's commit for a circuit drag; null places the family's default on this canvas alone. */
    onPlaceCircuit: ((ShapeKind, CircuitSpan) -> Unit)?,
    onTextPress: ((x: Double, y: Double, pageW: Double, pageH: Double) -> Unit)?,
    /** The text box the in-place field has taken over, or null — the sheet leaves it to the field. */
    editingText: Element? = null,
    /** What a double-click on the selected living element means — the shell's pencil, from the canvas. */
    onEditLiving: (() -> Unit)?,
    draft: SnapshotStateList<InkSample>,
    /** The tablet's stroke, drawn from its packets — a draft of its own, painted beside [draft]. */
    penDraft: SnapshotStateList<InkSample>,
    pen: PenContactStream?,
    /** The pen's own readings: who owns the stroke, the ring, the grab line and the traced region. */
    pointer: PenPointer,
    /** The page's recorded picture, so a frame replays the sheet instead of re-painting it. */
    sheetPicture: PagePicture,
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)? = null,
    measurer: TextMeasurer,
    density: Float,
    panMode: Boolean,
    dialWheel: () -> Boolean,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onViewport: (Viewport) -> Unit,
    onFit: (FitMode) -> Unit,
    onEdited: (Int) -> Unit,
) {
    // The pen's pointer: a ring the size of the tool instead of the desktop's arrow (`DesktopCursor.kt`).
    // Its position is `pointer.hover`, lifted into `PageCanvas` so the driver's own readings can move it.
    val ringPx = toolCursorDiameterPx(tool, tool.ringWidthPt(ink, shapeInk), sheet.zoom, density)
    // A mouse move that copies the contact must not drag the ring behind the packets still arriving.
    val followMouse = { System.nanoTime() - pointer.atNanos.value > PenPacketStroke.HOVER_STALE_NANOS }

    Canvas(
        Modifier.fillMaxSize().sheetInput(
            tab = tab,
            panMode = panMode,
            tool = tool,
            ink = ink,
            shapeInk = shapeInk,
            pen = pen,
            sheet = sheet,
            pointer = pointer,
            draft = draft,
            ringVisible = ringPx != null,
            followMouse = followMouse,
            dialWheel = dialWheel,
            targets = PagePressTargets(
                onPlaceLiving = onPlaceLiving,
                onPlaceCircuit = onPlaceCircuit,
                circuitKind = circuitKind,
                onTextPress = onTextPress,
                onEditLiving = onEditLiving,
                // Only the component is drawn live: a plot or table is still waiting for its values.
                livingPreview = pointer.previewLiving,
                onLiveDrag = onLiveDrag,
            ),
            density = density,
            onViewport = onViewport,
            onFit = onFit,
            onEdited = onEdited,
        ),
    ) {
        paintSheet(
            sheetPicture = sheetPicture,
            page = page,
            viewport = sheet.viewport,
            zoom = sheet.zoom,
            density = density,
            connectorKind = connectorKindFor(tool, circuitKind),
            measurer = measurer,
            backdrop = tab.document.backdrop,
            hiddenLayers = tab.hiddenOn(tab.page),
            tool = tool,
            ink = ink,
            shapeInk = shapeInk,
            // The drafts themselves, not copies: their **identity** is the key the painted geometry is
            // cached by (`StrokePathCache`), and a fresh list per frame would miss on every paint.
            drafts = listOf(draft, penDraft),
            grabLineY = pointer.grabLine.value,
            selection = tab.selection,
            previewRegion = pointer.previewRegion.value,
            previewLiving = pointer.previewLiving.value,
            paintProbe = paintProbe,
            skip = editingText,
        )
        // Above the sheet, in the canvas's pixels: the ring marks the point, not the page. Both readings are
        // taken **here**, in the draw scope, so a hover reading repaints the sheet and never recomposes it.
        val ring = ringPx ?: return@Canvas
        pointer.hover.value?.let { at -> drawToolCursor(at, ring) }
    }
}

/**
 * The sheet's **input surface**, as one modifier: the tool's pointer, the pen's readings, the wheel and the
 * gestures a press can start.
 *
 * The pointer over the page is the *tool's*: a ring is the pointer while a pen, a highlighter or an eraser is
 * in hand (an arrow beside it pointed at nothing), a hand for the hand, and the desktop's arrow for every
 * other slot (`DesktopCursor.toolPointerIcon`). Split out of the composable so the surface that owns a page
 * stays layout and wiring (`SourceStructureTest`).
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.sheetInput(
    tab: DesktopTab,
    panMode: Boolean,
    tool: DesktopTool,
    ink: DesktopInk,
    shapeInk: DesktopInk,
    pen: PenContactStream?,
    sheet: SheetLayout,
    pointer: PenPointer,
    draft: SnapshotStateList<InkSample>,
    /** Whether the tool draws a ring: the pointer is blank behind it, the arrow otherwise. */
    ringVisible: Boolean,
    /** A mouse move that is a copy of the contact must not drag the ring back (`PenPacketStroke`). */
    followMouse: () -> Boolean,
    dialWheel: () -> Boolean,
    targets: PagePressTargets,
    density: Float,
    onViewport: (Viewport) -> Unit,
    onFit: (FitMode) -> Unit,
    onEdited: (Int) -> Unit,
): Modifier = this
    .pointerHoverIcon(toolPointerIcon(tool, ringVisible = ringVisible, panMode = panMode))
    .hoverTracking(pointer, followMouse)
    .onPointerEvent(PointerEventType.Scroll) { event ->
        wheelViewport(
            event, sheet.viewport, sheet.zoom, density, sheet.pageW, sheet.pageH,
            sheet.viewW, sheet.viewH, onFit,
            forceZoom = dialWheel(),
        )?.let(onViewport)
    }
    .sheetGestures(
        tab, panMode, tool, ink, shapeInk, pen, sheet, pointer, draft, targets, onViewport, onEdited,
    )

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
    /** The connector whose **pins** the sheet marks, or null for a tool that cannot join anything. */
    connectorKind: ShapeKind? = null,
    measurer: TextMeasurer,
    backdrop: PageBackdrop?,
    hiddenLayers: Set<Int>,
    tool: DesktopTool,
    ink: DesktopInk,
    /** The pen's ink for a geometry slot — the picked tool's ink is only for its own live stroke. */
    shapeInk: DesktopInk,
    /**
     * The strokes in progress, one list per input path.
     *
     * Two, not one: the tablet's packets and the Compose pointer each own their draft, so a cancelled
     * mouse gesture can never erase the samples of a stroke the pen is still drawing. They are painted as
     * separate previews because they are separate strokes until one of them is committed.
     */
    drafts: List<List<InkSample>>,
    /** The vertical-space tool's grab line, in page points, or null when no such drag is live. */
    grabLineY: Double? = null,
    /** The live selection of the page being drawn, if any. */
    selection: PageSelection? = null,
    /** The region a selection drag is tracing right now, if any. */
    previewRegion: SelectionRegion? = null,
    /** The living element a placement drag is drawing right now, if any — a component being pulled out. */
    previewLiving: Element? = null,
    /** The ink probe's sink; null in the editor. Called after the ink, at the same clock reading. */
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)? = null,
    /** The element the in-place field is retyping, left out of the recorded sheet. */
    skip: Element? = null,
) {
    // Resolved before the transform: inside it the receiver is a DrawTransform, which has no display
    // density of its own.
    val startedAt = System.nanoTime()
    val pxPerPt = zoom * density
    // The sheet: recorded once per viewport, replayed per frame (see `PagePicture`). Recording is the
    // Kotlin cost — `LivingInk`, text measurement, a `Path` per stroke — and a frame must not pay it.
    sheetPicture.draw(this, page, viewport, zoom, density, backdrop, hiddenLayers, measurer, skip)
    withTransform({ translate(viewport.offsetX * density, viewport.offsetY * density) }) {
        paintLiveOverlay(
            page = page,
            tool = tool,
            ink = ink,
            shapeInk = shapeInk,
            connectorKind = connectorKind,
            drafts = drafts,
            previewLiving = previewLiving,
            selection = selection,
            previewRegion = previewRegion,
            grabLineY = grabLineY,
            pxPerPt = pxPerPt,
            measurer = measurer,
        )
    }
    // The probe times the paint itself, after the ink was laid down: that is the frame the eye saw.
    paintProbe?.invoke(drafts, System.nanoTime())
    // The watchdog counts every repaint, whichever path caused it — a window that paints while nothing is
    // happening is the failure it exists to catch — and times it, which is what the frame budget is.
    DesktopDiagnostics.painted(
        (System.nanoTime() - startedAt) / 1_000_000.0,
        sheetPicture.records,
        System.identityHashCode(page),
    )
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
internal fun PenPacketStrokes(
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
        // Leaving the packet path mid-stroke (a slot switch, a page change) must hand the pen back: the
        // ownership flag is what stands the Compose gesture aside, and a flag stuck on `true` would leave
        // the next slot's drag — a figure, a grab line — unable to start at all.
        onDispose {
            stop?.invoke()
            owns.value = false
        }
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

/** The desk behind the page: true black, as decided in `docs/ui-modernization.md`. */
private val DESK = DesktopTheme.desk

/**
 * The connector whose pins the sheet should mark: the line tool, or the circuit slot's armed part.
 * Everything else marks nothing, exactly as the tablet draws nothing for it.
 */
internal fun connectorKindFor(tool: DesktopTool, circuitKind: ShapeKind): ShapeKind? {
    val candidate = if (circuitFamilyOf(tool) != null) circuitKind else tool.shapeKind()
    return candidate?.takeIf { CircuitElementFactory.isConnectorKind(it) }
}

/** A page's on-screen size in dp at [zoom] — used by the sidebar's progress bar and the status bar. */
internal fun pageSizeDp(pageW: Double, pageH: Double, zoom: Float): Pair<Float, Float> =
    (pageW * zoom).toFloat() to (pageH * zoom).toFloat()

/** The zoom label a status bar shows. */
internal fun zoomLabel(zoom: Float): String = "${(zoom * 100f).roundToInt()}%"

/** A page's label: `Page 3 / 12`. */
internal fun pageLabel(page: Int, count: Int): String = "Page ${page + 1} / ${count.coerceAtLeast(1)}"
