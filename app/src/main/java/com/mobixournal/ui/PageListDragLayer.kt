package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.Page
import com.mobixournal.render.DrawingSurfaceView
import kotlin.math.roundToInt

/** How far the carried page reads as lifted off the list. */
private val DRAG_GHOST_SHADOW = 12.dp

/**
 * The drag of one page, held **by the list** rather than by the row's handle.
 *
 * This is what makes the auto-scroll behave like the window's: the list walks for as long as the finger
 * holds in an end strip, across the whole document. A drag owned by the row dies as soon as that row is
 * recycled — which is exactly what auto-scrolling does — so the gesture is read here, on a node that
 * outlives every row, and the page being carried is painted above the list ([PageDragGhost]).
 *
 * A press only starts a move inside the row's **handle band**, the last [DRAG_HANDLE_SIZE] of width: the
 * page number and the preview stay taps. The band is what the row's handle marks, and it is read off the
 * list's own width rather than off a row, so it cannot drift from the list's geometry.
 *
 * The drag is cleared in a `finally`, so a gesture taken away from us (a system swipe, the window losing
 * focus) can never leave the list walking on its own.
 */
internal suspend fun PointerInputScope.pageListDragGesture(
    drag: PageListDrag,
    listState: LazyListState,
    rowHeightPx: Float,
    pageCount: Int,
    onDrop: (from: Int, to: Int) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
        val scroll = scrollPxOf(listState, rowHeightPx)
        val index = ((down.position.y + scroll) / rowHeightPx).toInt()
        val inBand = down.position.x >= size.width - DRAG_HANDLE_SIZE.toPx()
        if (!inBand || index !in 0 until pageCount) return@awaitEachGesture
        // Claimed here rather than by the row: a tap that starts on the band is a move, never a scroll.
        down.consume()
        drag.start(index, scrollPxOf(listState, rowHeightPx))
        try {
            var released = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    released = true
                    change.consume()
                    break
                }
                val dy = change.positionChange().y
                if (dy != 0f) {
                    drag.by(dy)
                    change.consume()
                }
            }
            val from = drag.from
            val to = if (released) drag.landing(scrollPxOf(listState, rowHeightPx), pageCount) else null
            if (from != null && to != null) onDrop(from, to)
        } finally {
            drag.cancel()
        }
    }
}

/**
 * The page being carried, drawn **over** the list at the finger's height ([PageListDrag.liftedTop]).
 *
 * It is a card of the lane's own row height, carrying the page's number and its preview, rather than the
 * row itself lifted by a translation: a row can be recycled out of the list mid-drag, and a picture that
 * lives in a recycled row simply disappears from under the finger. Held above the list it stays, however
 * far the list walks beneath it.
 */
@Composable
internal fun PageDragGhost(
    drag: PageListDrag,
    listState: LazyListState,
    rowHeightPx: Float,
    pages: List<Page>,
    surface: DrawingSurfaceView?,
    picked: Set<Int>,
) {
    val from = drag.from ?: return
    val top = drag.liftedTop(from, scrollPxOf(listState, rowHeightPx))
    // A handle on a picked row carries the whole pick, so the card counts what travels.
    val carrying = if (from in picked && picked.size > 1) "${picked.size} pages" else "${from + 1}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PAGE_ROW_HEIGHT)
            .offset { IntOffset(0, top.roundToInt()) }
            .graphicsLayer { shadowElevation = DRAG_GHOST_SHADOW.toPx() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(PAGE_NUMBER_WIDTH).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                carrying,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        PageListPreview(page = pages.getOrNull(from), surface = surface, onGoTo = {})
    }
}
