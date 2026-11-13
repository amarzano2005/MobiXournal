package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.inkTool
import com.mobixournal.desktop.ink.shapeKind
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.StrokePathCache
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.ink.DraftStrokeCache
import com.mobixournal.desktop.ink.previewTool
import com.mobixournal.desktop.ink.shapeFromDrag
import com.mobixournal.desktop.ink.strokeOf
import com.mobixournal.desktop.io.drawPdfPreviewBands
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.DesktopTool

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
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.render.CircuitSnapping
import com.mobixournal.render.FigurePins
import com.mobixournal.render.PinFamily
import com.mobixournal.render.MagneticHighlighter
import com.mobixournal.render.ElementRef
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
    /**
     * The pins the gesture has magnetically caught — one for a move, one per end for a wire being drawn or a
     * part being placed — the targets the release will join.
     */
    magnetTargets: List<CircuitSnapping.PinTarget> = emptyList(),
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
    paintDraftPreviews(page, tool, ink, shapeInk, drafts, magnetic = magnetic, pxPerPt = pxPerPt)
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
    // A **move** owns the marks it shows: with no stroke being drawn and no part being pulled out, whatever
    // the selection arms marks its own pins — which are the actor's, never a target — so on a page with
    // nothing else to join it marks nothing (`FigurePins.hasCompany`), the insertion gesture's own rule.
    val actorRefs = if (drafts.all { it.isEmpty() } && previewLiving == null) {
        selection?.refs.orEmpty()
    } else {
        emptySet()
    }
    // The kind is the **caller's** answer, because it is the caller that knows whether a gesture is live: a
    // selection merely resting on the page marks nothing, exactly as on the tablet.
    paintConnectionMarks(page, connectorKind, previewLiving, selection, livingElement, actorRefs, pxPerPt)
    // The selection's marching outline and its handles, above the ink: what is caught stays visible.
    // A single living element also wears its own grips (a figure's corners, a table's dividers).
    selection?.let { drawSelection(it, pxPerPt, living = livingElement) }
    // Above the outline: *what* the move will join, while the hand is still down. The tablet paints the
    // same ring from the same target (`drawMagnetTarget`), so the two shells answer one question.
    magnetTargets.forEach { drawMagnetTarget(it, pxPerPt) }
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
 * The pin a move has caught, as a dot inside a ring at the target's page position.
 *
 * Chrome only: the target is the page's own pin, and nothing here is written to the document.
 */
private fun DrawScope.drawMagnetTarget(target: CircuitSnapping.PinTarget, pxPerPt: Float) {
    val at = Offset((target.absX * pxPerPt).toFloat(), (target.absY * pxPerPt).toFloat())
    val dot = MAGNET_DOT_PX / pxPerPt
    drawCircle(DesktopTheme.accent, radius = dot, center = at)
    drawCircle(
        color = DesktopTheme.accent,
        radius = dot * 1.7f,
        center = at,
        style = Stroke(width = 2.dp.toPx()),
    )
}

/** The magnet dot's radius in screen pixels — the tablet's handle dot, so both shells look alike. */
private const val MAGNET_DOT_PX = 5f

/**
 * Every pin the sheet wears for a **connection**, in one call: the marks of a connector in hand, of a part
 * being pulled out, and of a **selected** element — which wears its own pins *and* the compatible ones it can
 * be paired with, so a joint can be read end to end without arming a tool (`FigurePins.selectionMarks`).
 */
private fun DrawScope.paintConnectionMarks(
    page: Page,
    markedKind: ShapeKind?,
    previewLiving: Element?,
    selection: PageSelection?,
    selected: Element?,
    actorRefs: Set<ElementRef>,
    pxPerPt: Float,
) {
    drawConnectionPins(page, markedKind, previewLiving, pxPerPt, actorRefs)
    drawSelectionPins(page, selection, selected, pxPerPt)
}

/**
 * The pins a **selected** element wears, painted on the sheet — chrome, never ink.
 *
 * Both ends of the pairing, which is the point of selecting a figure: its **own** pins (filled, the thing
 * being joined) and the **compatible** ones it may be paired with (a wire's end, an arrow's tip). Without
 * the second half a reader saw only where a joint could land, never what it could land on. A selection on a
 * page that holds nothing to pair with stays bare (`FigurePins.selectionMarks`).
 */
private fun DrawScope.drawSelectionPins(
    page: Page,
    selection: PageSelection?,
    element: Element?,
    pxPerPt: Float,
) {
    if (selection == null || selection.refs.size != 1) return
    val marks = FigurePins.selectionMarks(page, element, exclude = selection.refs)
    val own = PIN_PENDING_PX / pxPerPt
    for ((x, y) in marks.own) {
        drawCircle(
            color = DesktopTheme.accent,
            radius = own,
            center = Offset((x * pxPerPt).toFloat(), (y * pxPerPt).toFloat()),
        )
    }
    val compatible = PIN_DOT_PX / pxPerPt
    for (target in marks.compatible) {
        drawCircle(
            color = DesktopTheme.accent.copy(alpha = 0.85f),
            radius = compatible,
            center = Offset((target.absX * pxPerPt).toFloat(), (target.absY * pxPerPt).toFloat()),
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
    page: Page,
    tool: DesktopTool,
    ink: DesktopInk,
    shapeInk: DesktopInk,
    drafts: List<List<InkSample>>,
    /** True while a magnetic highlighter's bands stand in for the freehand path. */
    magnetic: Boolean,
    pxPerPt: Float,
) {
    // A **figure being drawn** wears its own pins while the stylus is still down — its corners and the
    // middle of each side, the tablet's `drawPendingPins` — so the border a connector will attach to is
    // visible as it is laid down. The marks follow the gesture, never the armed tool.
    drawFigureDraftPins(page, tool, drafts, pxPerPt)
    val previewTool = tool.previewTool() ?: return
    if (magnetic) return
    val previewInk = if (tool.shapeKind() != null) shapeInk else ink
    for (draft in drafts) {
        if (draft.isEmpty()) continue
        val isFigure = tool.shapeKind() != null
        // **One read of the draft per frame.** The list is the pen's own state and its coroutine appends
        // to it while a frame paints it, so reading it twice — once for the points the geometry is built
        // from, once more for the length the cache key is built with — could take a point set from one
        // moment and a length from the next. That frame painted a stroke deformed at its head and the next
        // frame painted it correctly, which is the "it warps by itself for an instant" a hand sees. A copy
        // is taken once, here, and everything below reads that copy: the geometry, the key's length and the
        // draft's identity stay one consistent view (the copy holds the same sample objects, so the
        // incremental caches still recognise the stroke).
        val samples = runCatching { draft.toList() }.getOrNull() ?: continue
        if (samples.size < 2) continue
        (if (!isFigure) runCatching {
            DraftStrokeCache.stroke(draft, samples, previewTool, previewInk.colour, previewInk.width, previewInk.lineStyle)
        }.getOrNull() else strokeOf(
            previewTool,
            previewInk.colour,
            previewInk.width,
            samples,
            previewInk.lineStyle,
        ))?.let { draftStroke ->
            // The draft is a stroke growing by a few samples per frame: its geometry is cached by the
            // list it lives in and the length it has reached, so a frame that only redraws (a scroll, a
            // hover, a selection moving) does not rebuild the whole outline again.
            val shapes = StrokePathCache.draft(
                identity = draft,
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
    /** The elements the gesture is moving: their own pins are not marks of their own move. */
    actorRefs: Set<ElementRef> = emptySet(),
) {
    if (markedKind == null) return
    val dot = PIN_DOT_PX / pxPerPt
    val targets = CircuitSnapping.gestureTargets(markedKind, page, exclude = actorRefs).orEmpty()
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
    // Nothing on this page to join: the part's own pins would be marks on empty paper (`hasCompany`).
    if (targets.isEmpty()) return
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

/** The pins of a **figure in flight**, painted where its draft is being laid down. */
private fun DrawScope.drawFigureDraftPins(
    page: Page,
    tool: DesktopTool,
    drafts: List<List<InkSample>>,
    pxPerPt: Float,
) {
    val kind = tool.shapeKind() ?: return
    // A component's in-flight pins come from its own preview (`drawConnectionPins`), not from the draft.
    if (FigurePins.familyOf(kind) == PinFamily.CIRCUIT) return
    // The marks are worth showing only when the page holds something the drawing could join — the same rule
    // the tablet applies (`FigurePins.hasCompany`): on a page with no company the draft is bare.
    if (!FigurePins.hasCompany(page, kind)) return
    // The other half of the join: the wires' ends and arrows' tips already on the page, so the figure can be
    // lined up against them before the lift (`FigurePins.joinedBy`).
    val compatible = PIN_DOT_PX / pxPerPt
    for (target in FigurePins.joinedBy(page, kind)) {
        drawCircle(
            color = DesktopTheme.accent.copy(alpha = 0.85f),
            radius = compatible,
            center = Offset((target.absX * pxPerPt).toFloat(), (target.absY * pxPerPt).toFloat()),
        )
    }
    val dot = PIN_PENDING_PX / pxPerPt
    for (draft in drafts) {
        if (draft.size < 2) continue
        val points = draft.map { StrokePoint(it.x, it.y, 1.0) }
        for ((x, y) in FigurePins.pinsOf(kind, points)) {
            drawCircle(
                color = DesktopTheme.accent,
                radius = dot,
                center = Offset((x * pxPerPt).toFloat(), (y * pxPerPt).toFloat()),
            )
        }
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
