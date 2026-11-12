/**
 * The **shape draft**: one drag of a shape tool, as the canvas holds it between touch-down and lift-off.
 *
 * A shape gesture is not ink. Its first touch fixes an anchor and a width that stay put for the whole drag
 * — a shape ignores pressure, so the width is the tool's setting captured at the anchor, never a taper —
 * and every move replaces the *end* point, from which [ShapeBuilder] rebuilds the preview. That is the
 * whole state: `active`, the anchor, the end and the width. It lives here because five clusters read it
 * and four commit paths write it — figures, tables, circuit components and the connector magnet, plus the
 * pin overlay that draws where a component would land — and because the alternative was six members on
 * `DrawingSurfaceView` that any extension could reach and none owned.
 *
 * Two rules the object keeps, which used to be spread over the call sites:
 *
 *  - **`finish()` keeps the geometry.** The commit reads the anchor and the end *after* the gesture has
 *    ended (a table or a component is authored from them), so ending a draft only means "no longer in
 *    progress"; [begin] is what overwrites the points, at the next anchor.
 *  - **A tap is a drag under [TAP_TRAVEL_PT].** The table tool places a default-sized table on a tap and
 *    the rectangle drawn on a drag, and that threshold belongs to the draft, not to the table's commit.
 *
 * Android-free — plain doubles — so the whole state machine is unit-tested on the JVM ([ShapeDraftTest]).
 */
package com.mobixournal.render

import kotlin.math.hypot

internal class ShapeDraft {

    /** True while a shape gesture is in progress (a shape tool is down, and the commit has not run). */
    var active = false
        private set

    /**
     * The pin hold of this gesture, one per end (`EndsMagnet`): a wire being drawn and a part being placed
     * keep what they caught across frames instead of re-deciding on every sample. Reset by [begin].
     */
    val magnet = EndsMagnet()

    /** The drag's anchor, in page-local pt: where the first touch landed, after the grid and guide snaps. */
    var startX = 0.0
        private set
    var startY = 0.0
        private set

    /** The drag's live end, in page-local pt: what the preview is rebuilt from on every move. */
    var endX = 0.0
        private set
    var endY = 0.0
        private set

    /**
     * The width the shape draws at, in pt: the tool's width setting, fixed at the first touch and held
     * for the whole shape. See [nominalShapeWidthPt], which is where that number comes from.
     */
    var widthPt = 1.5
        private set

    /**
     * Open a gesture: a shape tool is down ([shape] true) or a freehand stroke is ([shape] false, which
     * also ends any shape the previous gesture left behind).
     */
    fun begin(shape: Boolean) {
        active = shape
        magnet.reset()
    }

    /** Anchor the draft at [x], [y] and pin its width; the end starts on the anchor, so a tap draws nothing. */
    fun place(x: Double, y: Double, widthPt: Double) {
        startX = x
        startY = y
        endX = x
        endY = y
        this.widthPt = widthPt
    }

    /** Move the live end — every drag sample, already snapped to the grid, the guide and any pin. */
    fun extend(x: Double, y: Double) {
        endX = x
        endY = y
    }

    /** The gesture is over. The anchor and the end stay readable: the commit builds from them. */
    fun finish() {
        active = false
    }

    /** Whether the gesture never really travelled — a tap, which a table places at a default size. */
    fun isTap(): Boolean = hypot(endX - startX, endY - startY) < TAP_TRAVEL_PT

    private companion object {
        /** Under this much travel (pt) a shape drag is a tap. */
        const val TAP_TRAVEL_PT = 6.0
    }
}
