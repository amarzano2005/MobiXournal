package com.mobixournal.desktop

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
 * The arithmetic is here and pure — screen pixels in, an offset delta out — so the band, the ramp and the
 * directions are a JVM test (`EdgePanTest`) rather than something to feel for with a mouse.
 */

/** How wide the band at each edge is, in screen pixels: about a fingertip, not a fifth of the window. */
internal const val EDGE_PAN_BAND_PX = 48f

/**
 * The fastest the view travels, in screen pixels per tick (~16 ms), so ~1 400 px/s at the border: quick
 * enough to cross a page, slow enough to stop where you meant to.
 */
internal const val EDGE_PAN_SPEED_PX = 22f

/** One edge-scroll tick: how long the loop waits for the pointer before it scrolls without it. */
internal const val EDGE_PAN_TICK_MS = 16L

/**
 * How far the view should scroll for a pointer at ([x], [y]) in a [viewW] × [viewH] canvas, in **screen
 * pixels to add to the viewport's offsets** — zero away from the band, growing linearly to
 * [EDGE_PAN_SPEED_PX] at the border and beyond.
 *
 * The sign is the viewport's, not the pointer's: a pointer near the **left** edge returns a **positive**
 * delta (the content slides right, revealing what is further left), and the right edge returns a negative
 * one. On a canvas narrower than two bands the band shrinks to half the extent, so the two edges meet in
 * the middle instead of overlapping — a small window keeps one direction per pointer position.
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
 * The view a selection drag works in: the mapping **as it is now** (the sheet moves while the drag is
 * running, so the one captured at press time is already stale), and the one write an edge scroll makes.
 *
 * A drag's page points are read through this every event, which is what makes edge scrolling behave: the
 * pointer is still, the sheet slides under it, and the point it names therefore travels — so the marquee
 * keeps growing and an element being dragged stays under the cursor while the page catches up.
 */
internal class SelectionView(
    private val viewport: () -> Viewport,
    /** The page's own size in points — what the drag's starting press is checked against. */
    val pageW: Float,
    val pageH: Float,
    private val viewW: Float,
    private val viewH: Float,
    private val onViewport: (Viewport) -> Unit,
) {
    /** The viewport in force now. */
    fun current(): Viewport = viewport()

    /** Scroll for a pointer resting at ([x], [y]) — pixels, as the pointer reports them. */
    fun panAtEdge(x: Float, y: Float, density: Float) {
        val (dx, dy) = edgePanDelta(x, y, viewW, viewH)
        if (dx == 0f && dy == 0f) return
        // The gesture survives the scroll: the canvas keys its pointer handler on the page and the zoom,
        // never on the offsets (`PageCanvas`), so moving the sheet does not cancel the drag that moved it.
        onViewport(current().pannedBy(dx / density, dy / density, pageW, pageH, viewW, viewH))
    }
}
