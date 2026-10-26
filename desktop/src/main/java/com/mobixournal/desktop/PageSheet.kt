package com.mobixournal.desktop

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
        packets = packets,
    )
    SheetCanvas(
        tab = tab,
        page = page,
        sheet = sheet,
        tool = tool,
        ink = ink,
        shapeInk = shapeInk,
        circuitKind = circuitKind,
        onPlaceLiving = onPlaceLiving,
        onPlaceCircuit = onPlaceCircuit,
        onTextPress = text.onPress,
        // The words being retyped belong to the field while it is up: the sheet must not paint them
        // underneath it as well.
        editingText = text.box?.editing,
        onEditLiving = onEditLiving,
        draft = draft,
        penDraft = penDraft,
        pen = pen,
        pointer = pointer,
        sheetPicture = sheetPicture,
        paintProbe = paintProbe,
        measurer = measurer,
        density = density,
        panMode = panMode,
        dialWheel = dialWheel,
        onLiveDrag = onLiveDrag,
        onViewport = onViewport,
        onFit = onFit,
        onEdited = onEdited,
    )
    // What floats over the sheet — the Text field and the selection's bar — in the page's own mapping.
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
    packets: PenPacketSource?,
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
        onPlaceLiving = placeLiving,
        onPlaceCircuit = placeCircuit,
        text = text,
        onEditLiving = onEditLiving,
        draft = draft,
        penDraft = penDraft,
        pen = pen,
        pointer = pointer,
        sheetPicture = sheetPicture,
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
    )
}
