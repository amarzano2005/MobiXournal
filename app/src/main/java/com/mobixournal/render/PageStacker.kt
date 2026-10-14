package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Page

/**
 * Placement of one page in the canvas, in view pixels. Each page is scaled to fit its column (the
 * view width divided by the column count, times the zoom factor); [topPx] is its top edge and
 * [leftPx] its left edge in content space (before scroll is applied). Rows are centred horizontally.
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
 * The laid-out document: one [PageBox] per page, the total content height, the content width (the
 * widest row, or the view width if wider — used to bound horizontal panning), and how many pages sit
 * side by side in a row.
 */
internal data class StackedLayout(
    val boxes: List<PageBox>,
    val totalHeightPx: Float,
    val contentWidthPx: Float,
    val columns: Int = 1,
) {

    /** The page whose vertical band contains [contentY] (content space), or null in a gap. */
    fun pageAt(contentY: Float): PageBox? =
        boxes.firstOrNull { contentY >= it.topPx && contentY < it.bottomPx }

    /**
     * The page under the content-space point, or null if the point falls in a gap. With one column
     * this is [pageAt]; with several it is the only way to tell the columns apart.
     */
    fun pageAt(contentX: Float, contentY: Float): PageBox? =
        boxes.firstOrNull { it.contains(contentX, contentY) }

    /**
     * The page under the point, falling back to the nearest one in the same row (and then to the
     * band) so a point in a gap still resolves — used for "which page am I looking at".
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
 * Lays document pages out in rows of [columns] pages, top-to-bottom, each fit to its column width
 * scaled by [zoom] and separated by [gapPx]. One column is the plain vertical stack; two or more is
 * the page-overview grid. Rows are centred, and pages shorter than their row sit at the row's top.
 *
 * The placement is computed from each page's **size** ([PageSource.pageSize]), never from the page
 * itself, so a `.mobix` whose page size its envelope declares is stacked without opening a single
 * page. The boxes resolve their pages through the same [PageSource], so the canvas reads exactly the
 * pages it draws.
 */
internal object PageStacker {

    /** Column counts the UI offers; index 0 is the plain single-page stack. */
    val COLUMN_CHOICES = listOf(1, 2, 3, 4)

    /** One page's placement while a row is being built, before the rows know their centring. */
    private data class Placed(
        val index: Int,
        val top: Float,
        val width: Float,
        val height: Float,
        val scale: Float,
    )

    /** Stack the pages already in memory — the same layout as [stack] with an in-memory source. */
    fun stack(
        pages: List<Page>,
        viewWidthPx: Int,
        gapPx: Float,
        zoom: Float = 1f,
        columns: Int = 1,
    ): StackedLayout = stack(PageSource.of(pages), viewWidthPx, gapPx, zoom, columns)

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
        columns: Int = 1,
    ): StackedLayout {
        if (viewWidthPx <= 0) return StackedLayout(emptyList(), 0f, 0f, 1)
        val cols = columns.coerceIn(1, COLUMN_CHOICES.last())
        // Each column gets an equal share of the width left over once the gaps between the columns
        // are taken out; one column is therefore the full view width, as before.
        val cellWidth = ((viewWidthPx - gapPx * (cols - 1)) / cols).coerceAtLeast(1f)

        val rows = ArrayList<List<Placed>>()
        var top = gapPx
        for (start in 0 until source.pageCount step cols) {
            val row = (start until minOf(start + cols, source.pageCount)).map { index ->
                val size = source.pageSize(index)
                val scale = if (size.widthPt > 0) (cellWidth / size.widthPt).toFloat() * zoom else zoom
                Placed(
                    index = index,
                    top = top,
                    width = (size.widthPt * scale).toFloat(),
                    height = (size.heightPt * scale).toFloat(),
                    scale = scale,
                )
            }
            rows += row
            top += (row.maxOfOrNull { it.height } ?: 0f) + gapPx
        }

        // Centre each row in the content band, which is the widest row or the view itself if wider.
        val rowWidth = rows.maxOfOrNull { row -> row.sumOf { it.width.toDouble() }.toFloat() + gapPx * (row.size - 1) }
        val contentWidth = maxOf(viewWidthPx.toFloat(), rowWidth ?: 0f)
        val boxes = ArrayList<PageBox>(rows.sumOf { it.size })
        for (row in rows) {
            val rowWidthPx = row.sumOf { it.width.toDouble() }.toFloat() + gapPx * (row.size - 1)
            var left = (contentWidth - rowWidthPx) / 2f
            for (placed in row) {
                boxes += PageBox(placed.index, placed.top, left, placed.width, placed.height, placed.scale, source)
                left += placed.width + gapPx
            }
        }
        return StackedLayout(boxes, top, contentWidth, cols)
    }
}
