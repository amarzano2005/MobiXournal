package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.io.CanvasGeometry
import kotlin.math.floor

/**
 * Where a board's **sheet** and one of its **tiles** sit in view space, in the units a [PageBox] takes:
 * a left/top in content pixels and a size, plus the pt size of the box's own "page" so the ruling it is
 * drawn with stays a small loop rather than one line per point of board.
 *
 * Pure — no Android types, no view — so the placement is unit-tested on the JVM, which is the only way
 * to pin the two things that are easy to get subtly wrong: that a tile is drawn exactly in the cell its
 * coordinate names, and that the sheet stays aligned with document coordinates as it scrolls.
 */
internal data class BoardPlacement(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    /** The pt size of the box's own page — see [BoardViewport.sheet]. */
    val pageWidthPt: Double,
    val pageHeightPt: Double,
)

/**
 * The geometry of an unbounded board as the canvas needs it: which cell a chunk is drawn in, and how
 * big a sheet to draw around the viewport.
 *
 * This is the one home for **document coordinates → view pixels** on a board, the role [PageBox] plays
 * for a page: a page's box can be asked `toViewX`/`toPtX` because its own origin is the document's,
 * while a board's origin is the mode's — the locked width's left edge for a vertical strip, an aligned
 * point on the ruling grid for an unbounded axis — and nothing on a chunk page says where it is.
 */
internal object BoardViewport {

    /**
     * The cell the chunk at ([cx], [cy]) is drawn in, in view space.
     *
     * The cell's origin is the coordinate times the **document's** tile size, not the chunk's own
     * declared width: the names `chunk_<cx>_<cy>` are a grid, and a chunk whose page is smaller than a
     * cell (a strip's pages are one paper each) still starts at the cell's corner — the cell is where
     * the content sits, the page is what the content measures.
     */
    fun tile(geometry: CanvasGeometry, cx: Int, cy: Int, zoom: Float): BoardPlacement {
        val cellPt = geometry.chunkSizePt
        return BoardPlacement(
            leftPx = (cx * cellPt * zoom).toFloat(),
            topPx = (cy * cellPt * zoom).toFloat(),
            widthPx = (cellPt * zoom).toFloat(),
            heightPx = (cellPt * zoom).toFloat(),
            pageWidthPt = cellPt,
            pageHeightPt = cellPt,
        )
    }

    /**
     * The sheet to draw around the viewport: the paper the board is written on.
     *
     * A strip mode locks one axis to the document's own dimension (`canvas.json`'s `widthPt` for a
     * vertical strip, its `heightPt` for a horizontal one) and the sheet is the band of that width
     * running away along the other; [CanvasMode.INFINITE_2D] locks neither and the sheet is the
     * viewport's own rectangle. The band is **aligned to the ruling grid** ([spacingPt]): its origin
     * sits on a multiple of the ruling spacing, so the lines the renderer rules from the box's own
     * page land on the same document positions as the viewport scrolls — a band cut anywhere else
     * would make the ruling crawl under the writing. The box's page is only as large as the band it
     * must cover (plus one spacing), which is what keeps the ruling a bounded loop however far out the
     * board has been scrolled.
     *
     * @param geometry The board's geometry.
     * @param zoom Zoom scale in px per pt (1.0 = 100%).
     * @param scrollX Viewport scroll in px.
     * @param scrollY Viewport scroll in px.
     * @param viewWidthPx Viewport size in px.
     * @param viewHeightPx Viewport size in px.
     * @param spacingPt The ruling step in pt, for the alignment.
     */
    fun sheet(
        geometry: CanvasGeometry,
        zoom: Float,
        scrollX: Float,
        scrollY: Float,
        viewWidthPx: Float,
        viewHeightPx: Float,
        spacingPt: Double,
    ): BoardPlacement {
        val scale = if (zoom > 0f) zoom else 1f
        val step = if (spacingPt > 0.0) spacingPt else 0.0
        // The pt the viewport's own top-left is at, and the pt of the aligned band origin around it.
        val originXPt = gridStart(scrollX / scale, step)
        val originYPt = gridStart(scrollY / scale, step)
        val bandWidthPt = viewWidthPx / scale
        val bandHeightPt = viewHeightPx / scale

        val lockX = geometry.mode == CanvasMode.INFINITE_VERTICAL
        val lockY = geometry.mode == CanvasMode.INFINITE_HORIZONTAL

        val widthPt = if (lockX) {
            geometry.widthPt.takeIf { it > 0.0 } ?: DrawingSurfaceDefaults.defaultPageWidth
        } else {
            bandWidthPt + step
        }
        val heightPt = if (lockY) {
            geometry.heightPt.takeIf { it > 0.0 } ?: DrawingSurfaceDefaults.defaultPageHeight
        } else {
            bandHeightPt + step
        }
        val leftPt = if (lockX) 0.0 else originXPt
        val topPt = if (lockY) 0.0 else originYPt
        return BoardPlacement(
            leftPx = (leftPt * scale).toFloat(),
            topPx = (topPt * scale).toFloat(),
            widthPx = (widthPt * scale).toFloat(),
            heightPx = (heightPt * scale).toFloat(),
            pageWidthPt = widthPt,
            pageHeightPt = heightPt,
        )
    }

    /**
     * The content extent a strip's **bound** implies, in px — what the viewport is allowed to scroll
     * across.
     *
     * A locked axis may not be panned past the sheet: the bound is the edge of the paper, so the
     * scrollable extent on that axis is the sheet's own size ([CanvasGeometry.fixedExtentPt] × [zoom])
     * and nothing wider. The other axis is unbounded in that mode, so the viewport's own size stands in
     * (its clamp ignores the extent there anyway) and the axis keeps running as you write.
     *
     * This is what lets a sheet **wider** than the screen still be reachable: a page stack's extent
     * would have fitted the paper to the width, clamping the scroll to zero and stranding the rest of
     * the strip off the right edge. A bound narrower than the view stays at the view's width, so the
     * scroll is pinned at 0 rather than going negative.
     */
    fun contentExtent(
        geometry: CanvasGeometry,
        zoom: Float,
        viewWidthPx: Float,
        viewHeightPx: Float,
    ): Pair<Float, Float> {
        val scale = if (zoom > 0f) zoom else 1f
        val width = when (geometry.mode) {
            CanvasMode.INFINITE_VERTICAL ->
                (geometry.widthPt.takeIf { it > 0.0 }
                    ?: DrawingSurfaceDefaults.defaultPageWidth) * scale
            CanvasMode.PAGINATED, CanvasMode.INFINITE_HORIZONTAL, CanvasMode.INFINITE_2D ->
                viewWidthPx.toDouble()
        }
        val height = when (geometry.mode) {
            CanvasMode.INFINITE_HORIZONTAL ->
                (geometry.heightPt.takeIf { it > 0.0 }
                    ?: DrawingSurfaceDefaults.defaultPageHeight) * scale
            CanvasMode.PAGINATED, CanvasMode.INFINITE_VERTICAL, CanvasMode.INFINITE_2D ->
                viewHeightPx.toDouble()
        }
        return maxOf(width.toFloat(), viewWidthPx) to maxOf(height.toFloat(), viewHeightPx)
    }

    /**
     * The largest multiple of [step] at or above [pt] — the step's own grid line the band starts on, so
     * the ruling is continuous with document `(0, 0)`. `0.0` when there is no step (an unruled sheet has
     * no phase to keep).
     */
    private fun gridStart(pt: Float, step: Double): Double =
        if (step <= 0.0) 0.0 else floor(pt.toDouble() / step) * step
}
