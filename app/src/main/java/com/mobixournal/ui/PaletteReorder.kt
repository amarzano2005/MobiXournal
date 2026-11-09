package com.mobixournal.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/**
 * The pen palette's **drag-to-reorder arithmetic**, kept apart from the gesture that feeds it.
 *
 * It follows the page list's own drag ([PageListDrag]): one row's measured height is the pitch, the
 * travel is rounded to a whole row so a swatch commits once it is more than half way into its
 * neighbour, and the ends stop rather than wrap — a colour that silently teleported would read as a
 * bug. Out-of-range indices and an unmeasured row answer the row that was picked up.
 */
internal fun paletteDropTarget(from: Int, offsetPx: Float, rowHeightPx: Int, count: Int): Int {
    if (from !in 0 until count || rowHeightPx <= 0) return from
    return (from + (offsetPx / rowHeightPx).roundToInt()).coerceIn(0, count - 1)
}

/**
 * [colors] with the entry at [from] moved to [to], keeping every other swatch in its relative order.
 *
 * A no-op — the same index, or an index past the end — answers the list unchanged, so a drag that
 * lands where it started writes nothing to the settings.
 */
internal fun movePaletteColor(colors: List<Int>, from: Int, to: Int): List<Int> {
    if (from !in colors.indices || to !in colors.indices || from == to) return colors
    return colors.toMutableList().also { it.add(to, it.removeAt(from)) }
}

/**
 * The palette list's drag: the row being carried, the finger's travel, and the page's scroll when the
 * gesture began.
 *
 * The page can be taller than the screen — the palette sits inside the settings page, not in a list of its
 * own — so the gesture has to answer the **page's** scroll as well as the finger: [liftedTop] is where the
 * eye sees the carried swatch (pinned to the finger, whatever the page does underneath it), while
 * [landing] reads the content that has travelled under it. [PaletteAutoScroll] walks the page while the
 * swatch sits in either end strip, which is what makes a long palette arrangeable in one gesture.
 */
internal class PaletteDragState {

    /** The row being carried, or -1 when nothing is in flight. */
    var from by mutableStateOf(-1)
        private set

    /** How far the finger has travelled, in px, sign included. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** One measured row's height, in px — the pitch a drop lands on. */
    var rowHeightPx by mutableFloatStateOf(0f)

    /** The first row's top in **content** px, so [liftedTop] survives the page scrolling. */
    var paletteTopPx by mutableFloatStateOf(0f)

    /** The page's scroll when the drag began, in content px. */
    private var startScroll by mutableFloatStateOf(0f)

    /** Begin at [index], remembering the page's scroll so later travel is measured in content px. */
    fun start(index: Int, scrollPx: Float) {
        from = index
        offset = 0f
        startScroll = scrollPx
    }

    /** Add one pointer event's worth of travel. */
    fun by(delta: Float) {
        offset += delta
    }

    fun clear() {
        from = -1
        offset = 0f
    }

    /** The carried row's top in **viewport** px — the finger's travel, not the page's. */
    fun liftedTop(index: Int): Float = paletteTopPx - startScroll + index * rowHeightPx + offset

    /** The row the swatch would land on right now, with the scroll's travel counted in. */
    fun landing(scrollPx: Float, count: Int): Int =
        paletteDropTarget(from, offset + scrollPx - startScroll, rowHeightPx.roundToInt(), count)
}

/**
 * Walks the settings page under a carried swatch while it sits in either end strip — deeper in the strip,
 * faster — so a palette longer than the screen can be rearranged without lifting the finger.
 *
 * The page is the scrolled surface, not a list of the palette's own: the rows are part of the settings
 * page, so the state walked is that page's [androidx.compose.foundation.ScrollState] and the strips are its
 * viewport's edges. The step is per **second** from the frame clock ([autoScrollStep], the page list's own
 * arithmetic), so a 120 Hz tablet and a 60 Hz one walk the page at the same speed; the page stops itself at
 * its ends, and a finger that leaves the strip stops it at once.
 */
@Composable
internal fun PaletteAutoScroll(drag: PaletteDragState, viewport: SettingsScrollViewport?) {
    if (viewport == null) return
    val edgePx = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    val speedPx = with(LocalDensity.current) { AUTO_SCROLL_SPEED.toPx() }
    LaunchedEffect(drag.from) {
        if (drag.from < 0) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            val from = drag.from
            if (from < 0) break
            val step = autoScrollStep(
                liftedTopPx = drag.liftedTop(from) - viewport.topPx,
                rowHeightPx = drag.rowHeightPx,
                viewportPx = viewport.heightPx,
                edgePx = edgePx,
                speedPx = speedPx,
            )
            if (step != 0f) viewport.state.scrollBy(step * seconds)
        }
    }
}
