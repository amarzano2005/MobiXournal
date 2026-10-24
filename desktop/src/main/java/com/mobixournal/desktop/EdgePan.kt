package com.mobixournal.desktop

import kotlin.math.abs
import kotlin.math.exp

/**
 * **Edge scrolling**: what a drag near the canvas's border asks the view to do.
 *
 * A marquee that runs off the sheet, or an element dragged past the bottom of the window, used to stop
 * dead at the border: the region or the element could only reach as far as the paper happened to be on
 * screen, so selecting a whole page or moving a figure to the next screenful meant selecting and dragging
 * again, in pieces. The view now scrolls while the pointer rests in a band at the edge, the way every
 * editor with a canvas does, and the gesture keeps running: the pointer does not have to move for the
 * sheet to keep travelling under it.
 *
 * The travel is **eased, not stepped** ([EdgeScroll]): the speed winds up over about a tenth of a second
 * instead of jumping to full on the first tick, unwinds the same way when the pointer leaves the band,
 * and is proportional to the time that actually passed — so an unlucky frame changes how smooth the
 * scroll is, never how fast it goes. Every drag that can reach the border shares this ([DragView]):
 * a selection, a figure being drawn, a vertical-space reflow.
 *
 * The arithmetic is here and pure — screen pixels in, an offset delta out — so the band, the ease and the
 * directions are JVM tests (`EdgePanTest`, `EdgeScrollTest`) rather than something to feel for with a mouse.
 */

/** How wide the band at each edge is, in screen pixels: about a fingertip, not a fifth of the window. */
internal const val EDGE_PAN_BAND_PX = 48f

/**
 * The speed at the border, in screen pixels per [EDGE_PAN_UNIT_MS]: ~1 400 px/s, quick enough to cross a
 * page, slow enough to stop where you meant to. It is a **speed**, not a step: what one tick moves is this
 * times the time that tick actually took.
 */
internal const val EDGE_PAN_SPEED_PX = 22f

/** The interval the speed is quoted over (16 ms, one frame at 60 Hz), never the interval ticks run at. */
internal const val EDGE_PAN_UNIT_MS = 16f

/**
 * How long the speed takes to get most of the way up to what the pointer asks for. Roughly a tenth of a
 * second: enough that entering the band reads as weight, short enough that the sheet answers the hand.
 */
internal const val EDGE_PAN_EASE_IN_MS = 90f

/**
 * How long it takes to **stop** when the pointer leaves the band: shorter than the wind-up, on purpose.
 * A glide as long as the ramp would carry the sheet a card's width past what the user aimed at; at 30 ms
 * the coast is about the width of the band itself — a sense of weight, not a free flight.
 */
internal const val EDGE_PAN_EASE_OUT_MS = 30f

/** One edge-scroll tick: how long the loop waits for the pointer before it scrolls without it. */
internal const val EDGE_PAN_TICK_MS = 8L

/** Ticks shorter or longer than this are clamped before they are turned into travel. */
internal const val EDGE_PAN_MIN_TICK_MS = 1f
internal const val EDGE_PAN_MAX_TICK_MS = 50f

/** Below this speed (px per unit) the scroll is over rather than drifting by hundredths of a pixel. */
internal const val EDGE_PAN_REST_PX = 0.02f

/**
 * How far the view should scroll for a pointer at ([x], [y]) in a [viewW] × [viewH] canvas, in **screen
 * pixels to add to the viewport's offsets** — zero away from the band, growing linearly to
 * [EDGE_PAN_SPEED_PX] at the border and beyond.
 *
 * This is the **velocity the position asks for**; the travel is [EdgeScroll]'s, which eases toward it. The
 * sign is the viewport's, not the pointer's: a pointer near the **left** edge returns a positive number
 * (the content slides right, revealing what is further left), and the right edge a negative one. On a
 * canvas narrower than two bands the band shrinks to half the extent, so the two edges meet in the middle
 * instead of overlapping — a small window keeps one direction per pointer position.
 */
internal fun edgePanDelta(
    x: Float,
    y: Float,
    viewW: Float,
    viewH: Float,
    band: Float = EDGE_PAN_BAND_PX,
    speed: Float = EDGE_PAN_SPEED_PX,
): Pair<Float, Float> {
    fun axis(position: Float, extent: Float): Float {
        val reach = minOf(band, extent / 2f)
        if (reach <= 0f) return 0f
        return when {
            position < reach -> ramp(reach - position, reach) * speed
            position > extent - reach -> -ramp(position - (extent - reach), reach) * speed
            else -> 0f
        }
    }
    return axis(x, viewW) to axis(y, viewH)
}

/** How much of the reach [depth] has crossed, from nothing at its inner edge to all of it at the border. */
private fun ramp(depth: Float, reach: Float): Float = (depth / reach).coerceIn(0f, 1f)

/**
 * The edge scroll's **inertia**: a velocity per axis that eases toward the one the pointer's position asks
 * for, and the travel that velocity implies over the time a tick really took.
 *
 * Two things make this feel governed rather than switched. The approach is exponential — with a longer
 * wind-up ([EDGE_PAN_EASE_IN_MS]) than brake ([EDGE_PAN_EASE_OUT_MS]) — so entering the band accelerates
 * instead of snapping to full speed and leaving it coasts to a stop instead of stopping dead, without a
 * glide that carries the sheet past what the user aimed at; and the step is `velocity × elapsed`, so a
 * slow or a fast frame is the same distance per second. A tick that arrives late cannot lurch the sheet.
 */
internal class EdgeScroll {
    private var vx = 0f
    private var vy = 0f

    /** Back to rest — a new drag starts from a standstill, never with the last gesture's momentum. */
    fun reset() {
        vx = 0f
        vy = 0f
    }

    /** One tick [dtMs] after the previous one: the travel it earns, in screen pixels to add to the view. */
    fun step(dtMs: Float, x: Float, y: Float, viewW: Float, viewH: Float): Pair<Float, Float> {
        val (targetX, targetY) = edgePanDelta(x, y, viewW, viewH)
        // Out of the band the target is nothing, and that is a stop: braking is quicker than winding up,
        // so the sheet settles where it was let go instead of sailing on.
        val ease = if (targetX == 0f && targetY == 0f) EDGE_PAN_EASE_OUT_MS else EDGE_PAN_EASE_IN_MS
        val approach = 1f - exp(-dtMs / ease)
        vx += (targetX - vx) * approach
        vy += (targetY - vy) * approach
        if (abs(vx) < EDGE_PAN_REST_PX) vx = 0f
        if (abs(vy) < EDGE_PAN_REST_PX) vy = 0f
        val elapsed = dtMs.coerceIn(EDGE_PAN_MIN_TICK_MS, EDGE_PAN_MAX_TICK_MS) / EDGE_PAN_UNIT_MS
        return (vx * elapsed) to (vy * elapsed)
    }
}

/**
 * The view a drag works in: the mapping **as it is now** (the sheet moves while the drag is running, so the
 * one captured at press time is already stale), and the one write an edge scroll makes.
 *
 * A drag's page points are read through this every event, which is what makes edge scrolling behave: the
 * pointer is still, the sheet slides under it, and the point it names therefore travels — so the marquee
 * keeps growing, an element being dragged stays under the cursor, and a figure keeps opening toward where
 * the pointer is while the page catches up.
 *
 * The ease is per drag: [beginDrag] puts it at rest, so a gesture starts from a standstill instead of
 * inheriting the momentum of the one before it.
 */
internal class DragView(
    private val viewport: () -> Viewport,
    /** The page's own size in points — what a drag's starting press is checked against. */
    val pageW: Float,
    val pageH: Float,
    private val viewW: Float,
    private val viewH: Float,
    private val density: Float,
    private val onViewport: (Viewport) -> Unit,
) {
    private val scroll = EdgeScroll()
    private var lastTickNanos = 0L
    private var session: Session? = null

    /** The drag in flight: where its pointer rests on screen, and what it re-applies when the sheet moves. */
    private class Session(var x: Float, var y: Float, val onScrolled: (Float, Float) -> Unit)

    /** The viewport in force now. */
    fun current(): Viewport = viewport()

    /**
     * A drag is starting at ([x], [y]), and [onScrolled] is what re-applies it when the edge scroll moves
     * the sheet — the drag's own "draws itself again from where the pointer now lands".
     *
     * The callback is a plain function, not a coroutine: a drag runs inside Compose's restricted pointer
     * scope, where the timer that ticks this cannot live. The ticker is launched once per canvas instead
     * (`pageGestures`) and calls in from outside.
     */
    fun beginDrag(x: Float, y: Float, onScrolled: (Float, Float) -> Unit) {
        scroll.reset()
        lastTickNanos = System.nanoTime()
        session = Session(x, y, onScrolled)
    }

    /** Where the drag's pointer is now — read by the ticker when no event is arriving. */
    fun moved(x: Float, y: Float) {
        session?.let {
            it.x = x
            it.y = y
        }
    }

    /** The drag is over: no more scrolling, and the ease is at rest for the next one. */
    fun endDrag() {
        session = null
    }

    /**
     * One tick: scroll for where the pointer rests, and — only if the sheet actually moved — let the drag
     * re-apply itself at that position. A pointer still in the middle therefore costs nothing at all.
     */
    fun tick(nowNanos: Long = System.nanoTime()) {
        val live = session ?: return
        if (!panAtEdge(live.x, live.y, nowNanos)) return
        live.onScrolled(live.x, live.y)
    }

    /** One step of the ease for a pointer at ([x], [y]); true when the viewport moved. */
    private fun panAtEdge(x: Float, y: Float, nowNanos: Long): Boolean {
        val elapsedMs = ((nowNanos - lastTickNanos) / 1_000_000.0).toFloat()
            .coerceIn(EDGE_PAN_MIN_TICK_MS, EDGE_PAN_MAX_TICK_MS)
        lastTickNanos = nowNanos
        val (dx, dy) = scroll.step(elapsedMs, x, y, viewW, viewH)
        if (dx == 0f && dy == 0f) return false
        // The gesture survives the scroll: the canvas keys its pointer handler on the page and the zoom,
        // never on the offsets (`PageCanvas`), so moving the sheet does not cancel the drag that moved it.
        onViewport(current().pannedBy(dx / density, dy / density, pageW, pageH, viewW, viewH))
        return true
    }
}

