package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.settings.DesktopTheme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The **edge bump**: what a scroll that runs into a side of the page says about it.
 *
 * A clamped pan is silent — the page simply stops, which on a big sheet reads as the gesture having failed
 * rather than as having reached the end of the paper. The window answers with a short flash along the side
 * that refused the travel, in the accent, fading over [EDGE_FLASH_MS]: enough to say *this is the edge*
 * while the hand is still moving, gone before it becomes chrome standing on the page.
 *
 * The rule is arithmetic and lives here rather than in three gestures: a writer asks for ([requestedX],
 * [requestedY]) dp and the clamp keeps less than that, so the difference is the travel a side refused. Every
 * writer that can hit an edge reports through [EdgeBumpState] — the hand's drag
 * (`SheetGestures.panByDrag`), the pen's own packets (`PenPacketPan.flush`) and the wheel, both when it steps
 * (`wheelViewport`) and when it glides (`SmoothScroller.scrollBy`) — and one layer paints them all.
 */
internal enum class Edge { LEFT, RIGHT, TOP, BOTTOM }

/** Which sides refused travel, and how hard: [strength] is 0.15…1, so a graze and a shove differ. */
internal data class EdgeHit(val edges: Set<Edge>, val strength: Float) {
    companion object {
        val NONE = EdgeHit(emptySet(), 0f)
    }
}

/**
 * The sides a write ran into: ([requestedX], [requestedY]) dp were asked for, [from] became [to].
 *
 * Null when the sheet travelled as far as it was asked to — most events, and the only honest answer in the
 * middle of a page. The sign is the side: an offset that wanted to **grow** was stopped by the page's
 * leading edge (`LEFT` / `TOP`), one that wanted to shrink by the trailing edge.
 */
internal fun edgeRefusal(from: Viewport, to: Viewport, requestedX: Float, requestedY: Float): EdgeHit? =
    edgeHitOf(
        refusedX = requestedX - (to.offsetX - from.offsetX),
        refusedY = requestedY - (to.offsetY - from.offsetY),
    )

/** The same, from the refused dp directly — what a glide's own clamp knows (its target, not a write). */
internal fun edgeHitOf(refusedX: Float, refusedY: Float): EdgeHit? {
    val edges = buildSet {
        if (refusedX >= EDGE_REFUSAL_DP) add(Edge.LEFT)
        if (refusedX <= -EDGE_REFUSAL_DP) add(Edge.RIGHT)
        if (refusedY >= EDGE_REFUSAL_DP) add(Edge.TOP)
        if (refusedY <= -EDGE_REFUSAL_DP) add(Edge.BOTTOM)
    }
    if (edges.isEmpty()) return null
    val strength = (maxOf(abs(refusedX), abs(refusedY)) / EDGE_STRENGTH_DP).coerceIn(0.15f, 1f)
    return EdgeHit(edges, strength)
}

/**
 * The window's edge reading, one per canvas: the last side a scroll ran into, and a counter that changes on
 * every hit so two bumps in a row are two flashes instead of one long one.
 *
 * Written by the gestures, read by [EdgeBumpLayer] — never the other way round: nothing here can move the
 * sheet, and a frame that paints it costs one gradient per refused side.
 */
internal class EdgeBumpState {

    var hit: EdgeHit by mutableStateOf(EdgeHit.NONE)
        private set

    /** Changes on every reported hit; the flash is keyed on it. */
    var seq: Int by mutableStateOf(0)
        private set

    /** A side refused travel, or nothing (a write that travelled as far as it asked reports nothing). */
    fun report(hit: EdgeHit?) {
        if (hit == null || hit.edges.isEmpty()) return
        this.hit = hit
        seq++
    }
}

@Composable
internal fun rememberEdgeBump(): EdgeBumpState = remember { EdgeBumpState() }

/**
 * The flash itself: a band along each side that refused travel, in the accent, fading from the hit's own
 * strength. Mounted over the canvas, so it never recomposes the sheet and never reaches the document.
 */
@Composable
internal fun EdgeBumpLayer(state: EdgeBumpState, modifier: Modifier = Modifier) {
    val flash = remember { Animatable(0f) }
    val seq = state.seq
    LaunchedEffect(seq) {
        val hit = state.hit
        if (hit.edges.isEmpty()) return@LaunchedEffect
        flash.snapTo(hit.strength)
        flash.animateTo(0f, tween(EDGE_FLASH_MS))
    }
    Canvas(modifier) {
        if (flash.value <= 0.01f) return@Canvas
        drawEdgeBump(state.hit.edges, flash.value)
    }
}

/** One gradient per refused side, fading inward from the window's own edge — a hint, never a border. */
internal fun DrawScope.drawEdgeBump(edges: Set<Edge>, alpha: Float) {
    val band = EDGE_BAND_DP.dp.toPx()
    val ink = DesktopTheme.accent
    val peak = ink.copy(alpha = EDGE_ALPHA * alpha)
    val clear = ink.copy(alpha = 0f)
    if (Edge.LEFT in edges) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(peak, clear), startX = 0f, endX = band),
            size = Size(band, size.height),
        )
    }
    if (Edge.RIGHT in edges) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(clear, peak), startX = size.width - band, endX = size.width),
            topLeft = Offset(size.width - band, 0f),
            size = Size(band, size.height),
        )
    }
    if (Edge.TOP in edges) {
        drawRect(
            brush = Brush.verticalGradient(listOf(peak, clear), startY = 0f, endY = band),
            size = Size(size.width, band),
        )
    }
    if (Edge.BOTTOM in edges) {
        drawRect(
            brush = Brush.verticalGradient(listOf(clear, peak), startY = size.height - band, endY = size.height),
            topLeft = Offset(0f, size.height - band),
            size = Size(size.width, band),
        )
    }
}

/** Travel below this is a pixel of rounding, not an edge: the clamp's own noise is not a bump. */
private const val EDGE_REFUSAL_DP = 0.5f

/** The refused travel that reads as a full-strength bump — a short shove into the edge. */
private const val EDGE_STRENGTH_DP = 12f

/** How wide the band is, in dp: a hint at the edge, not a border on the page. */
private const val EDGE_BAND_DP = 28f

/** The flash's own peak opacity, before the hit's strength scales it. */
private const val EDGE_ALPHA = 0.28f

/** How long a bump stays visible, in ms. */
internal const val EDGE_FLASH_MS = 240
