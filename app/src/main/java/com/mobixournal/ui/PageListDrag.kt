package com.mobixournal.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** How close to the list's top or bottom a dragged page starts pulling the list along. */
private val AUTO_SCROLL_EDGE = 56.dp

/** The fastest the list walks under a dragged page: roughly nine rows a second. */
private val AUTO_SCROLL_SPEED = 600.dp

/**
 * The drag of one page in the lateral list: the row it came from, how far the finger has carried it, and
 * the scroll the list had when the drag began.
 *
 * The landing row is measured in **content** pixels — the finger's travel plus however far the list has
 * walked under it. Without that sum a page dragged down a long document would keep claiming the row it
 * started from, however far the list had scrolled beneath it.
 */
internal class PageListDrag(private val rowHeightPx: Float) {

    /** The row the page came from, or null when nothing is being dragged. */
    var from by mutableStateOf<Int?>(null)
        private set

    /** How far the finger has travelled, in px, sign included. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** The list's scroll when the drag began, in content px. */
    private var startScroll by mutableFloatStateOf(0f)

    /** The shift that keeps the lifted page under a finger the list is scrolling beneath. */
    fun shift(scrollPx: Float): Float = scrollPx - startScroll

    /**
     * The lifted row's top in **viewport** pixels, lift included — where the eye sees the page.
     *
     * It is the layout position the row would have (`index * rowHeight - scroll`) plus the lift, which is
     * why it settles to `index * rowHeight + travel - startScroll`: **pinned to the finger**, and therefore
     * unchanged however far the list walks underneath. That is what lets [PageListAutoScroll] keep its strip
     * test true while the finger rests — reading the un-compensated layout position instead put the row back
     * into the middle of the list after one row of travel, so the scroll began and straight away stopped.
     */
    fun liftedTop(index: Int, scrollPx: Float): Float =
        index * rowHeightPx - scrollPx + offset + shift(scrollPx)

    /** The row the page would land on right now, clamped to the document. */
    fun landing(scrollPx: Float, pageCount: Int): Int? {
        val start = from ?: return null
        val travelled = offset + shift(scrollPx)
        return (start + (travelled / rowHeightPx).roundToInt())
            .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    /** Begin at [index], remembering the list's scroll so later travel can be measured in content px. */
    fun start(index: Int, scrollPx: Float) {
        from = index
        offset = 0f
        startScroll = scrollPx
    }

    /** Add one pointer event's worth of travel. */
    fun by(delta: Float) {
        offset += delta
    }

    fun cancel() {
        from = null
        offset = 0f
    }
}

/**
 * Walks the list under a dragged page while that page sits in either end strip — deeper in the strip,
 * faster — so a page can be carried across a long document without lifting the finger.
 *
 * The step is per **second**, taken from the frame clock, so a 120 Hz tablet and a 60 Hz one move the list
 * at the same speed. The strip is measured from the lifted row's own edges, not from the finger: the row
 * is what the eye watches, and it is the row that must say when it wants the list to move.
 */
@Composable
internal fun PageListAutoScroll(
    drag: PageListDrag,
    listState: LazyListState,
    rowHeightPx: Float,
    viewportPx: Int,
) {
    val edgePx = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    val speedPx = with(LocalDensity.current) { AUTO_SCROLL_SPEED.toPx() }
    LaunchedEffect(drag.from) {
        if (drag.from == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            val from = drag.from ?: break
            val step = autoScrollStep(
                liftedTopPx = drag.liftedTop(from, scrollPxOf(listState, rowHeightPx)),
                rowHeightPx = rowHeightPx,
                viewportPx = viewportPx,
                edgePx = edgePx,
                speedPx = speedPx,
            )
            if (step != 0f) listState.scrollBy(step * seconds)
        }
    }
}

/** The list's scroll offset in content px: every row is the same height, so this is exact. */
internal fun scrollPxOf(state: LazyListState, rowHeightPx: Float): Float =
    state.firstVisibleItemIndex * rowHeightPx + state.firstVisibleItemScrollOffset

/**
 * How fast the list should walk, in px **per second**, given where the lifted row sits on screen: negative
 * near the top edge, positive near the bottom, zero anywhere else in the middle.
 *
 * Pure, and the reason this can be tested at all: how the row's position answers the list's scrolling is
 * [PageListDrag.liftedTop]'s business, and this is only the strip's arithmetic.
 */
internal fun autoScrollStep(
    liftedTopPx: Float,
    rowHeightPx: Float,
    viewportPx: Int,
    edgePx: Float,
    speedPx: Float,
): Float {
    if (viewportPx <= 0) return 0f
    val bottom = liftedTopPx + rowHeightPx
    return when {
        liftedTopPx < edgePx -> -speedPx * ((edgePx - liftedTopPx) / edgePx).coerceIn(0f, 1f)
        bottom > viewportPx - edgePx ->
            speedPx * ((bottom - viewportPx + edgePx) / edgePx).coerceIn(0f, 1f)
        else -> 0f
    }
}
