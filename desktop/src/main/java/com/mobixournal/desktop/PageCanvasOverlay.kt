package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.render.CircuitSnapping
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.ShapeKind

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
            )?.let { draftStroke -> drawInkStroke(draftStroke, pxPerPt) }
        }
    }
    // The element a placement drag has drawn so far, in the same painter the page uses: a component is
    // pulled out of the sheet along the pen, so the drag promises exactly what the lift writes.
    previewLiving?.let { preview -> drawElements(listOf(preview), pxPerPt, measurer) }
    // The component pins a connector would join, as the tablet draws them: chrome, not ink, so they are
    // painted after the sheet and never written to the document. The pins of a component being pulled out
    // are filled, because they are the targets it is about to mint.
    if (connectorKind != null) {
        val dot = PIN_DOT_PX / pxPerPt
        for (target in CircuitSnapping.gestureTargets(connectorKind, page).orEmpty()) {
            drawCircle(
                color = DesktopTheme.accent.copy(alpha = 0.85f),
                radius = dot,
                center = Offset((target.absX * pxPerPt).toFloat(), (target.absY * pxPerPt).toFloat()),
            )
        }
        val preview = previewLiving as? CircuitElement
        if (preview != null) {
            for (target in CircuitSnapping.collectTargets(listOf(preview))) {
                drawCircle(
                    color = DesktopTheme.accent,
                    radius = PIN_PENDING_PX / pxPerPt,
                    center = Offset((target.absX * pxPerPt).toFloat(), (target.absY * pxPerPt).toFloat()),
                )
            }
        }
    }
    // The selection's marching outline and its handles, above the ink: what is caught stays visible.
    // A single living element also wears its own grips (a figure's corners, a table's dividers).
    val livingElement = selection?.takeIf { it.refs.size == 1 }?.let { picked ->
        val ref = picked.refs.first()
        page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)
    }
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

/** A connection point's ring, in screen pixels — the tablet's `PIN_DOT_PX`. */
private const val PIN_DOT_PX = 3.5f

/** The ring a component being placed would mint, in screen pixels. */
private const val PIN_PENDING_PX = 4.5f
