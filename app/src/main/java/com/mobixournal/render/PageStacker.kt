package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Page

/**
 * Placement of one page in the canvas, in view pixels. Each page is scaled to fit the view width (times
 * the zoom factor); [topPx] is its top edge and [leftPx] its left edge in content space (before scroll is
 * applied). A page narrower than the view is centred.
 *
 * The box holds the page's **geometry**, not the page model: its [page] is read through the [source]
 * every time it is asked for, so a layout of a 200-page notebook keeps no page alive on its own — the
 * source materialises a page when a box is drawn and its own window evicts the rest. For a page
 * already in memory (a `.xopp`, a new document) the read is a list lookup and returns the very same
 * object, so nothing about those documents changes.
 */
internal class PageBox(
    val index: Int,
    val topPx: Float,
    val leftPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val scale: Float,
    /** How this box reaches its page — the source the canvas reads the visible pages through. */
    private val source: PageSource,
) {
    /** The page model this box shows, materialised through the source when no one has it yet. */
    val page: Page get() = source.page(index)

    val bottomPx: Float get() = topPx + heightPx
    val rightPx: Float get() = leftPx + widthPx

    fun contains(contentX: Float, contentY: Float): Boolean =
        contentX >= leftPx && contentX < rightPx && contentY >= topPx && contentY < bottomPx

    /** View x (px, scrolled) → page x (pt), given the viewport's horizontal scroll in px. */
    fun toPtX(viewX: Float, scrollX: Float): Double = ((viewX + scrollX - leftPx) / scale).toDouble()

    /** View y (px, scrolled) → page y (pt), given the viewport's vertical scroll in px. */
    fun toPtY(viewY: Float, scrollY: Float): Double = ((viewY + scrollY - topPx) / scale).toDouble()

    /** Page x (pt) → view x (px), given the viewport's horizontal scroll in px. */
    fun toViewX(ptX: Double, scrollX: Float): Float = (ptX * scale + leftPx - scrollX).toFloat()

    /** Page y (pt) → view y (px), given the viewport's vertical scroll in px. */
    fun toViewY(ptY: Double, scrollY: Float): Float = (ptY * scale + topPx - scrollY).toFloat()
}

/**
 * The laid-out document: one [PageBox] per page, the total content height and the content width (the
 * widest page, or the view width if wider — used to bound horizontal panning).
 */
internal data class StackedLayout(
    val boxes: List<PageBox>,
    val totalHeightPx: Float,
    val contentWidthPx: Float,
) {

    /** The page whose vertical band contains [contentY] (content space), or null in a gap. */
    fun pageAt(contentY: Float): PageBox? =
        boxes.firstOrNull { contentY >= it.topPx && contentY < it.bottomPx }

    /** The page under the content-space point, or null if the point falls in a gap (or beside a page). */
    fun pageAt(contentX: Float, contentY: Float): PageBox? =
        boxes.firstOrNull { it.contains(contentX, contentY) }

    /**
     * The page a probe lands on, falling back to the nearest page when the point is beside it — used for
     * "which page am I looking at", where a point in the margin still belongs to the sheet next to it.
     */
    fun nearestPage(contentX: Float, contentY: Float): PageBox? =
        pageAt(contentX, contentY)
            ?: boxes.filter { contentY >= it.topPx && contentY < it.bottomPx }
                .minByOrNull { minOf(kotlin.math.abs(contentX - it.leftPx), kotlin.math.abs(contentX - it.rightPx)) }

    /** Boxes that intersect the visible window `[scrollY, scrollY + viewHeightPx)`. */
    fun visible(scrollY: Float, viewHeightPx: Float): List<PageBox> =
        boxes.filter { it.bottomPx > scrollY && it.topPx < scrollY + viewHeightPx }
}

/**
 * Lays document pages out in one vertical stack, top-to-bottom, each fit to the view width scaled by
 * [zoom] and separated by [gapPx]. Pages narrower than the view are centred; the document is a single
 * sheet after another, which is the only arrangement the editor has.
 *
 * The placement is computed from each page's **size** ([PageSource.pageSize]), never from the page
 * itself, so a `.mobix` whose page size its envelope declares is stacked without opening a single
 * page. The boxes resolve their pages through the same [PageSource], so the canvas reads exactly the
 * pages it draws.
 */
internal object PageStacker {

    /** Stack the pages already in memory — the same layout as [stack] with an in-memory source. */
    fun stack(
        pages: List<Page>,
        viewWidthPx: Int,
        gapPx: Float,
        zoom: Float = 1f,
    ): StackedLayout = stack(PageSource.of(pages), viewWidthPx, gapPx, zoom)

    /**
     * Stack every page [source] declares, reading only each page's size.
     *
     * @param source the pages to place, and how the resulting boxes reach them.
     * @param viewWidthPx the view's width in pixels; a non-positive width stacks nothing.
     */
    fun stack(
        source: PageSource,
        viewWidthPx: Int,
        gapPx: Float,
        zoom: Float = 1f,
    ): StackedLayout {
        if (viewWidthPx <= 0) return StackedLayout(emptyList(), 0f, 0f)
        // Each page's own size first: the content band is the widest page or the view, so a page wider
        // than the view (a zoom above 100 %) anchors the band and a narrower one is centred in it.
        class Placed(val index: Int, val scale: Float, val width: Float, val height: Float)
        val placed = List(source.pageCount) { index ->
            val size = source.pageSize(index)
            val scale = if (size.widthPt > 0) (viewWidthPx / size.widthPt).toFloat() * zoom else zoom
            Placed(index, scale, (size.widthPt * scale).toFloat(), (size.heightPt * scale).toFloat())
        }
        val contentWidth = maxOf(viewWidthPx.toFloat(), placed.maxOfOrNull { it.width } ?: 0f)
        val boxes = ArrayList<PageBox>(placed.size)
        // The **first** page starts at the content's own top, so it begins directly under the frame's
        // closing edge with no strip of desk between them — `docs/chrome-frame.md` owns the rod.
        // [gapPx] therefore separates pages, it is not a margin around the document.
        var top = 0f
        for (page in placed) {
            boxes += PageBox(page.index, top, (contentWidth - page.width) / 2f, page.width, page.height, page.scale, source)
            top += page.height + gapPx
        }
        return StackedLayout(boxes, top, contentWidth)
    }
}
