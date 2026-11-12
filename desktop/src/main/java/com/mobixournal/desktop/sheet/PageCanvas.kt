package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.shapeKind
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.FrameMeter
import com.mobixournal.desktop.ink.InkProbe
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.PenContactStream
import com.mobixournal.desktop.ink.PenPacketSource
import com.mobixournal.desktop.ink.PenPacketStroke
import com.mobixournal.desktop.ink.TabletWheel
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.io.DesktopKaraoke
import com.mobixournal.desktop.io.drawSelectedPdfText
import com.mobixournal.desktop.io.save
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.shell.DesktopDiagnostics
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.drawSearchHit
import com.mobixournal.desktop.shell.drawToolCursor
import com.mobixournal.desktop.shell.toolPointerIcon
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.PointerOwnership
import com.mobixournal.desktop.tools.TextBoxUi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.render.Bounds
import com.mobixournal.render.CircuitElementFactory
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.MagneticHighlighter
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.ShapeKind
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.DisposableEffect
import com.mobixournal.desktop.settings.DesktopNavigationState
import com.mobixournal.desktop.settings.LocalNavigationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    /** The triangle's and trapezoid's own answers, read by a figure drag (Settings ▸ Figures). */
    figures: FigureShape = FigureShape(),
    /** Whether freehand strokes snap to geometric primitives on lift or dwell (`figure.recognizeShapes`). */
    recognizeShapes: Boolean = false,
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
    /**
     * The room the floating tool strips take on this canvas ([ChromeInset]): a fitted page is framed in
     * what is left of it, so opening a document puts the sheet in the clear space beside the bar.
     */
    chrome: ChromeInset = ChromeInset.NONE,
    /** The pen-tip ring: up or not, what it holds, and where the nib is reported (`HudUi`). */
    hud: HudUi = HudUi(),
    modifier: Modifier = Modifier,
) {
    // The page, its drafts and the two readings the gesture scope shares: one call (`PageCanvasMemory`).
    val state = rememberPageCanvasMemory(tab, ink, circuitKind, onPlaceLiving, onEdited)
    BoxWithConstraints(modifier.nibReporting(state.canvas.memory, state.density, hud)) {
        CanvasBox(
            tab = tab,
            page = state.page,
            tool = tool,
            ink = ink,
            shapeInk = shapeInk,
            circuitKind = circuitKind,
            figures = figures,
            recognizeShapes = recognizeShapes,
            placeLiving = state.placeLiving,
            placeCircuit = onPlaceCircuit,
            text = text,
            onEditLiving = onEditLiving,
            draft = state.canvas.memory.draft,
            penDraft = state.canvas.memory.penDraft,
            pen = pen,
            pointer = state.canvas.memory.pointer,
            sheetPicture = state.canvas.picture,
            karaoke = state.canvas.karaoke,
            paintProbe = paintProbe,
            measurer = state.measurer,
            density = state.density,
            panMode = panMode,
            dialWheel = dialWheel,
            onLiveDrag = onLiveDrag,
            selectionBar = selectionBar,
            origin = { state.canvas.memory.origin },
            extent = { state.canvas.memory.extent },
            onViewport = onViewport,
            onFit = onFit,
            onEdited = onEdited,
            packets = packets,
            chrome = chrome,
            hud = hud,
        )
    }
}
/**
 * The ring follows the mouse — but only while the driver's packets are quiet, so a contact the driver has
 * already moved on from does not drag the ring back to where the emulated mouse still thinks it is.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.hoverTracking(pointer: PenPointer, followMouse: () -> Boolean): Modifier =
    onPointerEvent(PointerEventType.Enter) { event ->
        if (followMouse()) pointer.hover.value = event.changes.firstOrNull()?.position
    }
        .onPointerEvent(PointerEventType.Move) { event ->
            if (followMouse()) pointer.hover.value = event.changes.firstOrNull()?.position
        }
        .onPointerEvent(PointerEventType.Exit) { if (followMouse()) pointer.hover.value = null }

/**
 * A page's size and the viewport in force for it, in the window it is being shown in.
 *
 * [viewport] is **the one the sheet is painted in**, and [chrome] is the room the floating strips take —
 * the two facts a press needs to land where the page was drawn. Everything that maps a pointer goes
 * through [liveViewport] instead of recomputing the rule: a second derivation (that was `clamp`ed but not
 * centred in the chrome) put the ink a strip's width away from the pointer on a fitted page, and the error
 * shrank as the reader zoomed in, which is what made it look like a zoom bug.
 */
internal data class SheetLayout(
    val pageW: Float,
    val pageH: Float,
    val zoom: Float,
    val viewW: Float,
    val viewH: Float,
    val viewport: Viewport,
    val chrome: ChromeInset = ChromeInset.NONE,
) {
    /**
     * The viewport a pointer maps through, from the window's own scroll [offsets].
     *
     * Live, not captured: a drag that reaches the canvas's edge scrolls the sheet, so the mapping during a
     * gesture has to be the view the reader is looking at. The rule is [Viewport.inChrome] — the painter's
     * — and not a second, hand-rolled centring.
     */
    fun liveViewport(offsets: Viewport): Viewport = offsets.copy(zoom = zoom)
        .inChrome(chrome, pageW, pageH, viewW, viewH)
}

/**
 * The page's box and the zoom that fits it — and the one write the canvas makes to the tab's own
 * viewport.
 *
 * The status bar reports the *effective* zoom, which for a fitted page is the window's answer and not the
 * tab's stored one: publishing it keeps the readout true without a second source of truth. It happens
 * **only while a fit mode is on** — publishing it unconditionally is a stale-write race, where a zoom the
 * user asked for is overwritten by the fitted value of the *previous* frame.
 *
 * [chrome] is the room the floating strips take: a fitted page is measured and centred in what is left
 * of the canvas (`ChromeInset`), so a document opens beside the toolbar instead of under it.
 */
@Composable
internal fun sheetLayout(
    tab: DesktopTab,
    page: Page,
    viewW: Float,
    viewH: Float,
    chrome: ChromeInset = ChromeInset.NONE,
): SheetLayout {
    val pageW = page.width.toFloat()
    val pageH = page.height.toFloat()
    val zoom = tab.fit.zoomFor(pageW, pageH, viewW - chrome.width, viewH - chrome.height) ?: tab.viewport.zoom
    // The scale the status bar calls **100 %**: the page's width filling the room this canvas frames it in.
    val widthFitZoom = Viewport.fitWidthZoom(pageW, viewW - chrome.width)
    // A fitted page is re-centred every layout pass; a free one keeps its own offsets, clamped.
    val viewport = tab.viewport.copy(zoom = zoom).inChrome(chrome, pageW, pageH, viewW, viewH)
    LaunchedEffect(zoom, viewW, viewH, tab.fit, widthFitZoom) {
        tab.viewWidth = viewW; tab.viewHeight = viewH
        // Published even while the reader zooms freely: the reference is the window's, not the mode's.
        tab.widthFitZoom = widthFitZoom
        if (tab.fit != FitMode.NONE && tab.viewport.zoom != zoom) {
            tab.viewport = tab.viewport.copy(zoom = zoom)
        }
    }
    return SheetLayout(pageW, pageH, zoom, viewW, viewH, viewport, chrome)
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
    /** The triangle's and trapezoid's own answers, read by a figure drag (Settings ▸ Figures). */
    figures: FigureShape = FigureShape(),
    recognizeShapes: Boolean = false,
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
    /** The karaoke driver, while a clip sounds: the page is then painted directly, directive per stroke. */
    karaoke: DesktopKaraoke? = null,
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)? = null,
    measurer: TextMeasurer,
    density: Float,
    panMode: Boolean,
    dialWheel: () -> Boolean,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onViewport: (Viewport) -> Unit,
    onFit: (FitMode) -> Unit,
    onEdited: (Int) -> Unit,
    /** The pen-tip ring: up or not, what it holds, and that its press is never the page's. */
    hud: HudUi = HudUi(),
) {
    // The pen's pointer: a ring the size of the tool instead of the desktop's arrow (`SheetMark`).
    val mark = sheetMark(tool, ink, shapeInk, sheet, density, pointer)
    val navigation = LocalNavigationState.current
    val scroller = rememberSmoothScroller()
    DisposableEffect(tab) { onDispose { scroller.cancel() } }
    val overPage = rememberOverPage(pointer, sheet, density)
    // A surface over the page takes the pointer, and the canvas then carries **no** press handler at all:
    // consuming above it changed nothing, because the page reads presses it did not consume
    // ([PointerOwnership]).
    val live = PointerOwnership.pageIsLive

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().then(
                if (!live) {
                    Modifier
                } else {
                    Modifier.sheetInput(
                        tab = tab,
                        panMode = panMode,
                        tool = tool,
                        ink = ink,
                        shapeInk = shapeInk,
                        pen = pen,
                        sheet = sheet,
                        pointer = pointer,
                        draft = draft,
                        ringVisible = mark.ringPx != null,
                        followMouse = mark.followMouse,
                        dialWheel = dialWheel,
                        targets = sheetTargets(
                            onPlaceLiving, onPlaceCircuit, circuitKind, figures, onTextPress, onEditLiving,
                            pointer, onLiveDrag, recognizeShapes = recognizeShapes,
                        ),
                        density = density,
                        onViewport = onViewport,
                        onFit = onFit,
                        onEdited = onEdited,
                        hud = hud,
                        navigation = navigation,
                        scroller = scroller,
                        overPage = overPage.value,
                    )
                },
            ),
        ) {
            paintSheetOf(
                sheetPicture, page, sheet, tab, tool, ink, shapeInk, circuitKind, measurer, density,
                draft, penDraft, pointer, editingText, karaoke, paintProbe,
            )
            drawSheetChrome(tab, sheet, density, mark, pointer)
        }
        sheetLayers(hud, pointer, mark, density, tab, sheet)
    }
}


/**
 * Whether the pointer is over a page at all — outside it the sheet wears the arrow, because there is nothing
 * out there for a ring, a hand or a caret to act on (`PageCanvasSheetInput.overPage`).
 *
 * Derived rather than read, so a hover that stays on its own side of the border does not recompose the canvas
 * on every move: only crossing the paper's edge does.
 */
@Composable
private fun rememberOverPage(pointer: PenPointer, sheet: SheetLayout, density: Float): State<Boolean> =
    remember(pointer, sheet, density) {
        derivedStateOf {
            val at = pointer.hover.value
            if (at == null) {
                false
            } else {
                val (pageX, pageY) = sheet.viewport.toPage(at.x / density, at.y / density)
                pageX >= 0f && pageX <= sheet.pageW && pageY >= 0f && pageY <= sheet.pageH
            }
        }
    }

/**
 * The marks **above** the painted sheet, in the canvas's own pixels: a PDF text selection (page space, like
 * the marquee), the search hits, and the pen's ring at the point the tool will act.
 *
 * Kept out of [SheetCanvas] so the surface that owns a page stays its layout, its paint and its wiring
 * (`SourceStructureTest`). The hover reading is taken here, in the draw scope, so it repaints the sheet
 * without recomposing it.
 */
private fun DrawScope.drawSheetChrome(
    tab: DesktopTab,
    sheet: SheetLayout,
    density: Float,
    mark: SheetMark,
    pointer: PenPointer,
) {
    drawSelectedPdfText(tab, sheet.viewport.offsetX, sheet.viewport.offsetY, sheet.zoom, density)
    drawSearchHit(tab, sheet.viewport.offsetX, sheet.viewport.offsetY, sheet.zoom, density) // chrome, not ink
    val ring = mark.ringPx ?: return
    pointer.hover.value?.let { at -> drawToolCursor(at, ring, mark.ringColour, mark.eraser) }
}

/**
 * Everything a press on the sheet may mean, as one value: the two commits, the component in hand, the figure
 * variants, the text press and the drag `Esc` cancels.
 *
 * A function rather than an inline block so [SheetCanvas] stays the sheet's layout and its paint, and so the
 * set of meanings a press has is written in one place (`SourceStructureTest`).
 */
private fun sheetTargets(
    onPlaceLiving: (LivingSlot, Bounds) -> Unit,
    onPlaceCircuit: ((ShapeKind, CircuitSpan) -> Unit)?,
    circuitKind: ShapeKind,
    figures: FigureShape,
    onTextPress: ((x: Double, y: Double, pageW: Double, pageH: Double) -> Unit)?,
    onEditLiving: (() -> Unit)?,
    pointer: PenPointer,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    recognizeShapes: Boolean = false,
): PagePressTargets = PagePressTargets(
    onPlaceLiving = onPlaceLiving,
    onPlaceCircuit = onPlaceCircuit,
    circuitKind = circuitKind,
    figures = figures,
    recognizeShapes = recognizeShapes,
    onTextPress = onTextPress,
    onEditLiving = onEditLiving,
    // Only the component is drawn live: a plot or table is still waiting for its values.
    livingPreview = pointer.previewLiving,
    onLiveDrag = onLiveDrag,
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
internal fun DrawScope.paintSheet(
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
    /** The live vertical-space drag's offset, or null — the block below [grabLineY] is then replayed, not
     * rebuilt (`ShiftPictures`). */
    liveShift: LiveShift? = null,
    /** The instrument standing on this page, or null — view state, so it is chrome above the sheet. */
    guide: DesktopGuidePose? = null,
    /** The instrument a pose drag is holding right now, for the snap tick; null when nothing is posed. */
    guideAim: GuideAim? = null,
    /** The live selection of the page being drawn, if any. */
    selection: PageSelection? = null,
    magnetTargets: List<com.mobixournal.render.CircuitSnapping.PinTarget> = emptyList(),
    /** The region a selection drag is tracing right now, if any. */
    previewRegion: SelectionRegion? = null,
    /** The living element a placement drag is drawing right now, if any — a component being pulled out. */
    previewLiving: Element? = null,
    /** The magnetic highlighter's bands, which replace the freehand preview while a drag snaps. */
    pdfBands: List<MagneticHighlighter.Band> = emptyList(),
    /** The ink probe's sink; null in the editor. Called after the ink, at the same clock reading. */
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)? = null,
    /** The element the in-place field is retyping, left out of the recorded sheet. */
    skip: Element? = null,
    /** The karaoke driver: when it holds a clip, the sheet is painted directly rather than replayed. */
    karaoke: DesktopKaraoke? = null,
) {
    // Resolved before the transform: inside it the receiver is a DrawTransform, which has no display
    // density of its own.
    val startedAt = System.nanoTime()
    val pxPerPt = zoom * density
    paintRecordedSheet(
        sheetPicture = sheetPicture,
        page = page,
        viewport = viewport,
        zoom = zoom,
        density = density,
        backdrop = backdrop,
        hiddenLayers = hiddenLayers,
        measurer = measurer,
        skip = skip,
        karaoke = karaoke,
        liveShift = liveShift,
    )
    withTransform({ translate(viewport.offsetX * density, viewport.offsetY * density) }) {
        paintLiveOverlay(
            page = page,
            tool = tool,
            ink = ink,
            shapeInk = shapeInk,
            connectorKind = connectorKind,
            drafts = drafts,
            previewLiving = previewLiving,
            pdfBands = pdfBands,
            selection = selection,
            magnetTargets = magnetTargets,
            previewRegion = previewRegion,
            grabLineY = grabLineY,
            guide = guide,
            guideAim = guideAim,
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
    // And the same frame, for the status bar: the watchdog writes a file nobody reads unless it is told to,
    // the meter answers the question a reader asks when a stroke feels late (`FrameMeter`).
    FrameMeter.painted()
}

/**
 * The sheet itself: the recorded picture, or — while a clip sounds — the page painted from its elements.
 *
 * A recording is replayed per frame (see `PagePicture`) because recording is the Kotlin cost: `LivingInk`,
 * text measurement, a `Path` per stroke, and a frame must not pay it. A recording says every stroke as it
 * was written, so it cannot show a page being written; a sounding clip paints the elements directly, under
 * the picture's own matrix, and `karaoke` carries the stroke it is on.
 */
private fun DrawScope.paintRecordedSheet(
    sheetPicture: PagePicture,
    page: Page,
    viewport: Viewport,
    zoom: Float,
    density: Float,
    backdrop: PageBackdrop?,
    hiddenLayers: Set<Int>,
    measurer: TextMeasurer,
    skip: Element?,
    karaoke: DesktopKaraoke?,
    liveShift: LiveShift?,
) {
    // The window's one shadow, around the sheet's **on-screen** rectangle: a halo is chrome, so a zoom must
    // not scale it and a thumbnail (whose surface is the page itself) must not pay for it.
    drawPageShadow(
        androidx.compose.ui.geometry.Rect(
            left = viewport.offsetX * density,
            top = viewport.offsetY * density,
            right = viewport.offsetX * density + (page.width * density).toFloat() * zoom,
            bottom = viewport.offsetY * density + (page.height * density).toFloat() * zoom,
        ),
    )
    val karaokeState = karaoke?.state
    // A vertical-space drag replays the sheet in two halves at an offset; the page it was recorded from is
    // untouched, so nothing is re-recorded while the hand is down (`ShiftPictures`). It comes **before** the
    // karaoke branch: a sounding clip is painted directive per stroke and cannot be split at the line, and a
    // drag is not a reading gesture — the animation resumes when the hand lifts.
    if (liveShift != null) {
        sheetPicture.drawShifted(
            this, page, viewport, zoom, density, backdrop, hiddenLayers, measurer, skip, liveShift,
        )
        return
    }
    if (karaokeState == null) {
        sheetPicture.draw(this, page, viewport, zoom, density, backdrop, hiddenLayers, measurer, skip)
        return
    }
    withTransform({
        translate(viewport.offsetX * density, viewport.offsetY * density)
        scale(zoom, zoom, pivot = Offset.Zero)
    }) {
        drawPageContent(
            page = page,
            pxPerPt = density,
            textMeasurer = measurer,
            backdrop = backdrop,
            hiddenLayers = hiddenLayers,
            skip = skip,
            karaoke = karaokeState,
        )
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

/**
 * The zoom label a status bar shows: **100 % is the page's width filling the window**
 * (`zoomPercentOfWidthFit`), the scale a phone opens a sheet at. [widthFitZoom] is that reference as the
 * canvas last measured it ([sheetLayout]); 0 — no page laid out yet — falls back to the absolute scale.
 */
internal fun zoomLabel(zoom: Float, widthFitZoom: Float = 0f): String =
    "${zoomPercentOfWidthFit(zoom, widthFitZoom)}%"

/** A page's label: `Page 3 / 12`. */
internal fun pageLabel(page: Int, count: Int): String = "Page ${page + 1} / ${count.coerceAtLeast(1)}"

/**
 * The state one page needs before anything is drawn: the page itself, its drafts and recorded picture, the
 * measurer, and the two readings the gesture scope cannot take for itself (the density, and what a lifted
 * living-slot drag means).
 *
 * A value rather than five locals in [PageCanvas], so the composable that lays the canvas out reads as one
 * object instead of a preamble.
 */
private class PageCanvasMemory(
    val measurer: TextMeasurer,
    val placeLiving: (LivingSlot, Bounds) -> Unit,
    val density: Float,
    val page: Result<Page>,
    val canvas: CanvasState,
)

@Composable
private fun rememberPageCanvasMemory(
    tab: DesktopTab,
    ink: DesktopInk,
    circuitKind: ShapeKind,
    onPlaceLiving: ((LivingSlot, Bounds) -> Unit)?,
    onEdited: (Int) -> Unit,
): PageCanvasMemory = PageCanvasMemory(
    measurer = rememberTextMeasurer(),
    // The one meaning a lifted living-slot drag has, chosen before the gesture runs.
    placeLiving = onPlaceLiving ?: defaultPlacer(tab, ink, circuitKind, onEdited),
    // Read once here: the gesture scope and the packet path both need it, and neither is in a draw scope.
    density = LocalDensity.current.density,
    page = rememberedPage(tab),
    // The drafts, the recorded picture and the karaoke driver: one call, keyed on the page itself.
    canvas = rememberCanvasState(tab),
)
