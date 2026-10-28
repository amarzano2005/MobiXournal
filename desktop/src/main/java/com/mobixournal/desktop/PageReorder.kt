package com.mobixournal.desktop

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/**
 * The page list's **drag-to-reorder arithmetic**, kept apart from the gesture that feeds it.
 *
 * Two decisions are worth having in one testable place rather than inside a pointer handler: which row a
 * pointer is over once the list has scrolled under it, and how fast the list should scroll while the
 * pointer rests against an edge. Both are pure numbers over row spans, so `PageReorderTest` pins them
 * without a window.
 */
internal object PageReorder {

    /** One row of the list as the drag sees it: its index and its span, in the viewport's own pixels. */
    data class Row(val index: Int, val top: Float, val height: Float)

    /**
     * The row the pointer at [y] would land on.
     *
     * A pointer above the first row answers the first and one below the last answers the last, so a drag
     * held past the end still drops somewhere sensible instead of nowhere.
     */
    fun target(rows: List<Row>, y: Float, fallback: Int): Int {
        if (rows.isEmpty()) return fallback
        rows.firstOrNull { y >= it.top && y <= it.top + it.height }?.let { return it.index }
        return if (y < rows.first().top) rows.first().index else rows.last().index
    }

    /**
     * How far to scroll **this frame** while a drag holds the pointer near an edge: negative up, positive
     * down, zero in the middle band.
     *
     * The speed follows the distance into the edge band rather than switching on at full speed at the
     * boundary, so a pointer just inside the edge creeps and one pinned against it moves a screen a second.
     */
    fun scrollStep(y: Float, viewportHeight: Float, edge: Float, maxStep: Float): Float {
        if (viewportHeight <= 0f || edge <= 0f) return 0f
        val depth = when {
            y < edge -> (edge - y) / edge
            y > viewportHeight - edge -> (y - (viewportHeight - edge)) / edge
            else -> return 0f
        }
        return (if (y < edge) -1f else 1f) * maxStep * depth.coerceIn(0f, 1f)
    }
}

/**
 * The list's one drag, as state: which page is lifted, where the pointer is in the list's own pixels, and
 * which row it would land on.
 *
 * Reading the layout on each move (rather than caching the rows at the start) is what makes dragging
 * correct while the list **scrolls under the pointer**: the rows move, the pointer does not, and the drop
 * target is recomputed from where things are now.
 */
@Stable
internal class PageDragHandle(private val list: LazyListState) {

    /** The page being dragged, or -1 when nothing is lifted. */
    var from by mutableStateOf(-1)
        private set

    /** The row the lifted page would land on, or -1 while nothing is dragged. */
    var target by mutableStateOf(-1)
        private set

    /** Where the pointer is, in the list viewport's pixels. */
    private var y by mutableStateOf(0f)

    val active: Boolean get() = from >= 0

    /** Lift page [index]; [offsetInRow] is where the press landed inside its row. */
    fun start(index: Int, offsetInRow: Float) {
        val row = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        from = index
        target = index
        y = row.offset + offsetInRow
    }

    /** Follow the pointer by [dy] pixels, and answer which row that is now. */
    fun moveBy(dy: Float) {
        if (!active) return
        y += dy
        target = PageReorder.target(rows(), y, from)
    }

    /** The move to commit, or null when the drag landed where it started; either way the drag ends. */
    fun drop(): Pair<Int, Int>? {
        val move = if (active && target >= 0 && target != from) from to target else null
        cancel()
        return move
    }

    fun cancel() {
        from = -1
        target = -1
        y = 0f
    }

    /** How far the list should scroll this frame while the pointer rests in an edge band. */
    fun autoScroll(edge: Float, maxStep: Float): Float {
        if (!active) return 0f
        return PageReorder.scrollStep(y, list.layoutInfo.viewportSize.height.toFloat(), edge, maxStep)
    }

    private fun rows(): List<PageReorder.Row> = list.layoutInfo.visibleItemsInfo.map { info ->
        PageReorder.Row(info.index, info.offset.toFloat(), info.size.toFloat())
    }
}

/** The list's edge band and top scrolling speed, in dp: a pointer's worth of "keep going". */
internal object PageDragMetrics {
    val EDGE = 56.dp
    val MAX_STEP = 10.dp
}
