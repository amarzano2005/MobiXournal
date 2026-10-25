package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.render.CircuitSnapping
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.ShapeKind
import kotlin.math.hypot

/**
 * Everything a page wears **above** its recorded sheet, in the page's own coordinate space: the stroke
 * being drawn, the element being pulled out, the pins a connection would join, the selection and its
 * handles, and the vertical-space grab line.
 *
 * It is drawn by the same rules as the ink it sits over — the figure's preview is the figure's own points
 * through the shared `strokeOf`, and the pins are the tablet's `CircuitSnapping` targets — so a preview
 * never promises something the lift would not write. Chrome only: nothing here is in the document.
 *
 * Split out of `PageCanvas.kt` because the sheet's painting is already its own subject and the file has a
 * line budget (`SourceStructureTest`); [pxPerPt] is the zoom × density every point is mapped with.
 */
internal fun DrawScope.paintLiveOverlay(
    page: Page,
    tool: DesktopTool,
    ink: DesktopInk,
    /** The pen's ink for a geometry slot — the picked tool's ink is only for its own live stroke. */
    shapeInk: DesktopInk,
    /** The connector whose **pins** the sheet marks, or null for a tool that cannot join anything. */
    connectorKind: ShapeKind?,
    /** The strokes in progress, one list per input path (the Compose pointer's, the tablet's). */
    drafts: List<List<InkSample>>,
    /** The living element a placement drag is drawing right now, if any — a component being pulled out. */
    previewLiving: Element?,
    selection: PageSelection?,
    previewRegion: SelectionRegion?,
    grabLineY: Double?,
    pxPerPt: Float,
    measurer: TextMeasurer,
) {
    // A geometry slot's preview is the figure's own points, drawn with the pen's rule: same width,
    // same colour, and the same list the lift reads, so what is previewed is what is written.
    val previewTool = tool.previewTool()
    if (previewTool != null) {
        val previewInk = if (tool.shapeKind() != null) shapeInk else ink
        for (draft in drafts) {
            if (draft.isEmpty()) continue
            strokeOf(
                previewTool,
                previewInk.colour,
                previewInk.width,
                draft,
                previewInk.lineStyle,
            )?.let { draftStroke ->
                // The draft is a stroke growing by a few samples per frame: its geometry is cached by the
                // list it lives in and the length it has reached, so a frame that only redraws (a scroll, a
                // hover, a selection moving) does not rebuild the whole outline again.
                val shapes = StrokePathCache.draft(
                    identity = draft,
                    size = draft.size,
                    tool = draftStroke.tool,
                    lineStyle = draftStroke.lineStyle,
                    points = draftStroke.points,
                    scale = pxPerPt,
                )
                drawStrokeShapes(draftStroke, shapes, pxPerPt)
            }
        }
    }
    // The element a placement drag has drawn so far, in the same painter the page uses: a component is
    // pulled out of the sheet along the pen, so the drag promises exactly what the lift writes.
    previewLiving?.let { preview -> drawElements(listOf(preview), pxPerPt, measurer) }
    // The one element the selection holds, when it is a single living recipe — drawn below by its grips,
    // and here to answer *which* connector's pins the sheet marks.
    val livingElement = selection?.takeIf { it.refs.size == 1 }?.let { picked ->
        val ref = picked.refs.first()
        page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)
    }
    // The component pins a connector would join, as the tablet draws them: chrome, not ink, so they are
    // painted after the sheet and never written to the document. The pins of a component being pulled out
    // are filled, because they are the targets it is about to mint. A part that was placed, then picked
    // up by the selection, keeps its pins marked: that is the moment the user drags it onto another pin,
    // and without the dots the magnet worked invisibly (the slot that placed it is gone).
    drawConnectionPins(
        page = page,
        markedKind = connectorKind ?: selectionConnectorKind(page, selection),
        previewLiving = previewLiving,
        pxPerPt = pxPerPt,
    )
    // The selection's marching outline and its handles, above the ink: what is caught stays visible.
    // A single living element also wears its own grips (a figure's corners, a table's dividers).
    selection?.let { drawSelection(it, pxPerPt, living = livingElement) }
    previewRegion?.let { drawSelectionPreview(it, pxPerPt) }
    // Above the ink, as the tablet draws it: the line the vertical-space drag would move the page at.
    grabLineY?.let { y ->
        drawLine(
            color = DesktopTheme.accent,
            start = Offset(0f, (y * pxPerPt).toFloat()),
            end = Offset((page.width * pxPerPt).toFloat(), (y * pxPerPt).toFloat()),
            strokeWidth = 1f,
        )
    }
}

/**
 * The pins a connection could join, the joins the page already holds, and the pins of the part being pulled
 * out — the tablet's own marks, in the page's coordinates.
 *
 * Chrome, never ink: nothing here is in the document. The dots are drawn for a **connector** ([markedKind]) and
 * for a **selected** part, which is when a pin can be aimed at after the part was placed.
 */
internal fun DrawScope.drawConnectionPins(
    page: Page,
    markedKind: ShapeKind?,
    previewLiving: Element?,
    pxPerPt: Float,
) {
    if (markedKind == null) return
    val dot = PIN_DOT_PX / pxPerPt
    val targets = CircuitSnapping.gestureTargets(markedKind, page).orEmpty()
    for (target in targets) {
        drawCircle(
            color = DesktopTheme.accent.copy(alpha = 0.85f),
            radius = dot,
            center = Offset((target.absX * pxPerPt).toFloat(), (target.absY * pxPerPt).toFloat()),
        )
    }
    // A **join** the page already holds: two pins in one place are drawn as a ring, so a connection that was
    // made is visible on the sheet instead of being an invisible agreement between two overlapping dots.
    for ((index, a) in targets.withIndex()) {
        for (b in targets.drop(index + 1)) {
            if (hypot(a.absX - b.absX, a.absY - b.absY) <= JOIN_PT) {
                drawCircle(
                    color = DesktopTheme.accent,
                    radius = JOIN_PX / pxPerPt,
                    center = Offset((a.absX * pxPerPt).toFloat(), (a.absY * pxPerPt).toFloat()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
    val preview = previewLiving as? CircuitElement ?: return
    for (pin in CircuitSnapping.collectTargets(listOf(preview))) {
        // The pin the magnet has caught this frame — the drag shows *that it will join* before the pen is
        // lifted, which is the only moment the person can still aim.
        val caught = targets.any { hypot(it.absX - pin.absX, it.absY - pin.absY) <= JOIN_PT }
        drawCircle(
            color = DesktopTheme.accent,
            radius = (if (caught) JOIN_PX else PIN_PENDING_PX) / pxPerPt,
            center = Offset((pin.absX * pxPerPt).toFloat(), (pin.absY * pxPerPt).toFloat()),
            style = if (caught) Stroke(width = 2.dp.toPx()) else Fill,
        )
    }
}

/** A connection point's ring, in screen pixels — the tablet's `PIN_DOT_PX`. */
private const val PIN_DOT_PX = 3.5f

/** The ring a component being placed would mint, in screen pixels. */
private const val PIN_PENDING_PX = 4.5f

/** How close two pins must be to count as one join, in page points — a little over a pixel. */
private const val JOIN_PT = 0.75

/** The ring a **joined** pair wears, in screen pixels: bigger than a pin's dot, so the join reads. */
private const val JOIN_PX = 7f
