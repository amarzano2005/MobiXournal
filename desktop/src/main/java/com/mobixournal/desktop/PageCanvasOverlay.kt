package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.render.CircuitSnapping
import com.mobixournal.render.MagneticHighlighter
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
    /**
     * The magnetic highlighter's bands, in page points, or empty when the drag draws freehand.
     *
     * While a highlighter drag snaps to a PDF page's text lines (`MagneticHighlighter`), these bands are
     * painted **in place of** the freehand preview: the sheet shows the flat band the lift will write, not
     * the wavy path it would have written without the text layer underneath.
     */
    pdfBands: List<MagneticHighlighter.Band> = emptyList(),
    selection: PageSelection?,
    previewRegion: SelectionRegion?,
    grabLineY: Double?,
    /**
     * The instrument standing on **this** page, or null — the caller filters the tab's pose by its page.
     *
     * A guide is view state, so it is painted here rather than in the recorded picture: the sheet holds no
     * element for it, and undoing an edit never has to restore it.
     */
    guide: DesktopGuidePose? = null,
    /** The instrument a pose drag is holding right now, for the snap tick (null when nothing is posed). */
    guideAim: GuideAim? = null,
    pxPerPt: Float,
    measurer: TextMeasurer,
) {
    // The instrument first, under the stroke being drawn: the amber wash is 13 % of the sheet, so ink laid
    // along it reads as ink on paper ruled by a tool rather than as ink hidden under chrome.
    paintPlacedGuide(guide, pxPerPt, guideAim)
    // A geometry slot's preview is the figure's own points; a snapping highlighter's preview is its bands,
    // not the hand's path underneath them.
    val magnetic = pdfBands.isNotEmpty() && tool.inkTool() == com.mobixournal.format.model.Tool.HIGHLIGHTER
    paintDraftPreviews(tool, ink, shapeInk, drafts, magnetic = magnetic, pxPerPt = pxPerPt)
    // The snapped bands, at the highlighter's own translucency: a preview of the ink, not chrome.
    drawPdfPreviewBands(pdfBands, ink.colour, pxPerPt)
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
 * The strokes **in progress**, one preview per input path, painted with the rule the lift will use: a
 * geometry slot's figure from the same points `shapeFromDrag` built, and everything else through the shared
 * `strokeOf`. The drafts themselves, not copies, are the key the painted geometry is cached by — a fresh
 * list per frame would miss on every paint.
 */
private fun DrawScope.paintDraftPreviews(
    tool: DesktopTool,
    ink: DesktopInk,
    shapeInk: DesktopInk,
    drafts: List<List<InkSample>>,
    /** True while a magnetic highlighter's bands stand in for the freehand path. */
    magnetic: Boolean,
    pxPerPt: Float,
) {
    val previewTool = tool.previewTool() ?: return
    if (magnetic) return
    val previewInk = if (tool.shapeKind() != null) shapeInk else ink
    for (draft in drafts) {
        if (draft.isEmpty()) continue
        val samples = runCatching { draft.toList() }.getOrNull() ?: continue
        if (samples.size < 2) continue
        strokeOf(
            previewTool,
            previewInk.colour,
            previewInk.width,
            samples,
            previewInk.lineStyle,
        )?.let { draftStroke ->
            // The draft is a stroke growing by a few samples per frame: its geometry is cached by the
            // list it lives in and the length it has reached, so a frame that only redraws (a scroll, a
            // hover, a selection moving) does not rebuild the whole outline again.
            val isFigure = tool.shapeKind() != null
            val shapes = StrokePathCache.draft(
                identity = draft,
                size = draft.size,
                tool = draftStroke.tool,
                lineStyle = draftStroke.lineStyle,
                points = draftStroke.points,
                scale = pxPerPt,
                smooth = !isFigure,
            )
            drawStrokeShapes(draftStroke, shapes, pxPerPt)
        }
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
