package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.inksByPackets
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.PenContactStream
import com.mobixournal.desktop.ink.PenPacketPan
import com.mobixournal.desktop.ink.PenPacketSource
import com.mobixournal.desktop.ink.PenPacketStrokes
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopKaraoke
import com.mobixournal.desktop.io.pdfTextCopyUi
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.TextBoxUi

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import com.mobixournal.format.model.Page
import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.ShapeKind

/**
 * The page inside the box the canvas measured: the tablet's packets first (they draw their own stroke),
 * then the sheet's canvas, which paints the page and takes the Compose pointer.
 *
 * Split out of [PageCanvas] rather than inlined: the canvas keeps the state a page needs, this keeps the
 * two children that spend it, which is the hundred-line rule `SourceStructureTest` enforces.
 */
@Composable
internal fun PageSheet(
    tab: DesktopTab,
    page: Page,
    sheet: SheetLayout,
    tool: DesktopTool,
    ink: DesktopInk,
    shapeInk: DesktopInk,
    circuitKind: ShapeKind,
    /** The triangle's and trapezoid's own answers, read by a figure drag. */
    figures: FigureShape = FigureShape(),
    recognizeShapes: Boolean = false,
    onPlaceLiving: (LivingSlot, Bounds) -> Unit,
    /** The shell's commit for a circuit drag; null places the family's default on this canvas alone. */
    onPlaceCircuit: ((ShapeKind, CircuitSpan) -> Unit)?,
    text: TextBoxUi,
    /** What a double-click on the selected living element means: open its editor. */
    onEditLiving: (() -> Unit)?,
    draft: SnapshotStateList<InkSample>,
    penDraft: SnapshotStateList<InkSample>,
    /** The pen's own readings: who owns the stroke, the ring, the grab line and the traced region. */
    pen: PenContactStream?,
    pointer: PenPointer,
    sheetPicture: PagePicture,
    /** The karaoke driver, while a clip sounds — the page is then drawn from its elements, per stroke. */
    karaoke: DesktopKaraoke? = null,
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)?,
    measurer: TextMeasurer,
    density: Float,
    panMode: Boolean,
    dialWheel: () -> Boolean,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    selectionBar: SelectionBarUi,
    origin: () -> Offset,
    extent: () -> Size,
    onViewport: (Viewport) -> Unit,
    onFit: (FitMode) -> Unit,
    onEdited: (Int) -> Unit,
    packets: PenPacketSource?,
    /** The pen-tip ring: up or not, what it holds, and that its press is never the page's. */
    hud: HudUi = HudUi(),
) {
    PenPaths(
        tab = tab,
        tool = tool,
        ink = ink,
        sheet = sheet,
        panMode = panMode,
        density = density,
        draft = penDraft,
        pointer = pointer,
        origin = origin,
        extent = extent,
        onViewport = onViewport,
        onEdited = onEdited,
        recognizeShapes = recognizeShapes,
        packets = packets,
        hud = hud,
    )
    SheetCanvas(
        tab = tab,
        page = page,
        sheet = sheet,
        tool = tool,
        ink = ink,
        shapeInk = shapeInk,
        circuitKind = circuitKind,
        figures = figures,
        recognizeShapes = recognizeShapes,
        onPlaceLiving = onPlaceLiving,
        onPlaceCircuit = onPlaceCircuit,
        onTextPress = text.onPress,
        // The words being retyped belong to the field: the sheet must not paint them underneath it.
        editingText = text.box?.editing,
        onEditLiving = onEditLiving,
        draft = draft,
        penDraft = penDraft,
        pen = pen,
        pointer = pointer,
        sheetPicture = sheetPicture,
        karaoke = karaoke,
        paintProbe = paintProbe,
        measurer = measurer,
        density = density,
        panMode = panMode,
        dialWheel = dialWheel,
        onLiveDrag = onLiveDrag,
        onViewport = onViewport,
        onFit = onFit,
        onEdited = onEdited,
        hud = hud,
    )
    SheetOverlaysOf(tab, text, sheet, density, selectionBar, extent)
}

/**
 * What floats over the sheet — the Text field and the selection's bar — in the page's own mapping.
 *
 * A named composable because the page's own layout is already the canvas and this is the one piece that is not
 * a surface of the page: it reads the mapping rather than taking part in it.
 */
@Composable
private fun SheetOverlaysOf(
    tab: DesktopTab,
    text: TextBoxUi,
    sheet: SheetLayout,
    density: Float,
    selectionBar: SelectionBarUi,
    extent: () -> Size,
) {
    SheetOverlays(
        text = text,
        zoom = sheet.zoom,
        density = density,
        offsetX = sheet.viewport.offsetX,
        offsetY = sheet.viewport.offsetY,
        selectionBounds = tab.selection?.bounds,
        selectionBar = selectionBar,
        canvas = extent(),
        viewW = sheet.viewW,
        viewH = sheet.viewH,
        pdfText = pdfTextCopyUi(tab),
    )
}

/**
 * The tablet's **two packet paths**, composed for the slots each owns.
 *
 * A slot whose product is not ink draws it from the Compose drag, so the pen's packets must not take the
 * pen under it: the stroke path is composed only for the slots that lay ink down or rub it out, and the
 * rule is stated (and tested) where a contact on the glass with Line or Vertical space picked draws no ink
 * and still runs its drag. The **hand** is the other half — its pan comes from the same packets, because
 * Windows' copy of this pen is coalesced — and neither path inks, so one contact is claimed exactly once:
 * a held Space takes the packets from the stroke path even when a pen is the picked slot.
 */
@Composable
private fun PenPaths(
    tab: DesktopTab,
    tool: DesktopTool,
    ink: DesktopInk,
    sheet: SheetLayout,
    panMode: Boolean,
    density: Float,
    draft: SnapshotStateList<InkSample>,
    pointer: PenPointer,
    origin: () -> Offset,
    extent: () -> Size,
    onViewport: (Viewport) -> Unit,
    onEdited: (Int) -> Unit,
    recognizeShapes: Boolean = false,
    packets: PenPacketSource?,
    /** The pen-tip ring: while it is up the packets answer it and never the page. */
    hud: HudUi = HudUi(),
) {
    // A held **Space** is the hand's pan with any slot picked, so it takes the packets away from the
    // stroke path: a pen that inked while Space was down would draw a line across the page it is
    // scrolling.
    if (tool.inksByPackets() && !panMode) {
        PenPacketStrokes(
            tab = tab,
            tool = tool,
            ink = ink,
            density = density,
            pageW = sheet.pageW,
            pageH = sheet.pageH,
            viewport = sheet.viewport,
            draft = draft,
            onEdited = onEdited,
            origin = origin,
            extent = extent,
            owns = pointer.owns,
            hover = pointer.hover,
            penAtNanos = pointer.atNanos,
            bands = pointer.pdfBands,
            // The stroke path is told about the ring so a tip that lands on a face commits it and lays no
            // ink on the sheet beneath — the pen's own packets are the one path the ring cannot see.
            hud = hud,
            recognizeShapes = recognizeShapes,
            packets = packets,
        )
    }
    if (packets != null && (tool == DesktopTool.HAND || panMode)) {
        PenPacketPan(
            viewport = sheet.viewport,
            pageW = sheet.pageW,
            pageH = sheet.pageH,
            viewW = sheet.viewW,
            viewH = sheet.viewH,
            density = density,
            onViewport = onViewport,
            owns = pointer.owns,
            packets = packets,
        )
    }
}

/**
 * The measured box the sheet is drawn in: the page — or the reason it could not be read — and the sheet's
 * own children. Below the canvas's own layout, above nothing it needs to decide, so `PageCanvas` stays the
 * state a page needs.
 */
@Composable
internal fun BoxWithConstraintsScope.CanvasBox(
    tab: DesktopTab,
    page: Result<Page>,
    tool: DesktopTool,
    ink: DesktopInk,
    shapeInk: DesktopInk,
    circuitKind: ShapeKind,
    /** The triangle's and trapezoid's own answers, read by a figure drag. */
    figures: FigureShape = FigureShape(),
    recognizeShapes: Boolean = false,
    placeLiving: (LivingSlot, Bounds) -> Unit,
    /** The shell's commit for a circuit drag, or null to place the family's default here. */
    placeCircuit: ((ShapeKind, CircuitSpan) -> Unit)?,
    text: TextBoxUi,
    onEditLiving: (() -> Unit)?,
    draft: SnapshotStateList<InkSample>,
    penDraft: SnapshotStateList<InkSample>,
    pen: PenContactStream?,
    pointer: PenPointer,
    sheetPicture: PagePicture,
    /** The karaoke driver, while a clip sounds — the sheet is then painted per stroke. */
    karaoke: DesktopKaraoke? = null,
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)?,
    measurer: TextMeasurer,
    density: Float,
    panMode: Boolean,
    dialWheel: () -> Boolean,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    selectionBar: SelectionBarUi,
    origin: () -> Offset,
    extent: () -> Size,
    onViewport: (Viewport) -> Unit,
    onFit: (FitMode) -> Unit,
    onEdited: (Int) -> Unit,
    packets: PenPacketSource?,
    /** The room the floating strips take, so a fitted page is framed beside them (`ChromeInset`). */
    chrome: ChromeInset,
    /** The pen-tip ring: up or not, what it holds, and that its press is never the page's. */
    hud: HudUi = HudUi(),
) {
    val viewW = maxWidth.value
    val viewH = maxHeight.value
    val loaded = page.getOrNull()
    if (loaded == null) {
        PageReadFailure(page.exceptionOrNull()?.message)
        return
    }
    PageSheet(
        tab = tab,
        page = loaded,
        sheet = sheetLayout(tab, loaded, viewW, viewH, chrome),
        tool = tool,
        ink = ink,
        shapeInk = shapeInk,
        circuitKind = circuitKind,
        figures = figures,
        recognizeShapes = recognizeShapes,
        onPlaceLiving = placeLiving,
        onPlaceCircuit = placeCircuit,
        text = text,
        onEditLiving = onEditLiving,
        draft = draft,
        penDraft = penDraft,
        pen = pen,
        pointer = pointer,
        sheetPicture = sheetPicture,
        karaoke = karaoke,
        paintProbe = paintProbe,
        measurer = measurer,
        density = density,
        panMode = panMode,
        dialWheel = dialWheel,
        onLiveDrag = onLiveDrag,
        selectionBar = selectionBar,
        origin = origin,
        extent = extent,
        onViewport = onViewport,
        onFit = onFit,
        onEdited = onEdited,
        packets = packets,
        hud = hud,
    )
}
