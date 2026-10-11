package com.mobixournal.render

import com.mobixournal.format.model.Background
import kotlin.math.PI
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Pure snapping geometry — pulling page points onto the page background's ruling, and a selection
 * rotation onto a fixed angular step, the way desktop Xournal++ does. Kept free of Android types so
 * it is unit-testable on the JVM; [DrawingSurfaceView] applies it while a shape drag, the guide's
 * ruling snap or a rotate handle is live, gated on the user's settings toggles.
 *
 * A background only snaps along the axes it actually rules: `lined`/`ruled` sheets have horizontal
 * lines and so snap Y only, `graph`/`dotted` snap both, and a plain sheet snaps nothing. The ruling
 * is resolved as a [Lattice] — spacing *and* phase — so a snapped point always lands on a line the
 * renderer really drew.
 *
 * An **isometric** sheet rules no axis at all: it is a ±30° triangular mesh, so its [Lattice] carries
 * a [Mesh] and snapping resolves the nearest **node** of the two interleaved sub-lattices the crossing
 * lines make (see [isometricMesh]). Its phase is derived from the page width the paper is ruled
 * against, so a caller resolving an isometric page passes that width in.
 */
object Snapping {

    /** The rotation increment, in degrees, a snapped rotate handle lands on. */
    const val ROTATION_STEP_DEG: Double = 15.0

    /**
     * A page's ruling as a **lattice to snap onto**: how far apart its lines sit on each axis, and
     * where the lattice starts.
     *
     * The origin is not always the page corner. A `graph`/`dotted` sheet rules *from its `m1`
     * margin*, so its lines lie on `m1 + n·spacing`; snapping to bare multiples of the spacing would
     * pull ink off the very line the user can see. `lined`/`ruled` sheets rule from the page's top
     * edge, so their phase is zero. A rule that constrains a single axis ([stepX] or [stepY] zero)
     * leaves the other coordinate alone — that is what makes a lined sheet snap vertically only.
     */
    data class Lattice(
        /** Line-to-line distance on the X axis in pt, or 0 when the ruling rules no verticals. */
        val stepX: Double,
        /** Line-to-line distance on the Y axis in pt, or 0 when the ruling rules no horizontals. */
        val stepY: Double,
        /** X of the first ruled line (pt): 0 for lined/ruled, the margin for graph/dotted. */
        val phaseX: Double,
        /** Y of the first ruled line (pt): 0 for lined/ruled, the margin for graph/dotted. */
        val phaseY: Double,
        /**
         * The **interleaved node mesh** this ruling rules instead of axis-aligned lines, or null.
         *
         * An isometric sheet is the one case: its two ±30° families of lines cross at nodes the
         * rendered paper really draws, but there is no horizontal or vertical line to snap an axis
         * onto, so [stepX]/[stepY] are zero and [snap] resolves the nearest **node** through this.
         */
        val mesh: Mesh? = null,
    ) {
        /** True when this ruling constrains at least one axis, or rules a node mesh. */
        val active: Boolean get() = stepX > 0.0 || stepY > 0.0 || mesh != null

        /**
         * [x] pulled onto the nearest ruled vertical, or left alone when the ruling rules none.
         *
         * Axis-wise by construction: an interleaved mesh ([mesh]) has no vertical line, so this is
         * the identity there and the node is resolved by [snap] instead.
         */
        fun snapX(x: Double): Double =
            if (stepX > 0.0) phaseX + round((x - phaseX) / stepX) * stepX else x

        /** [y] pulled onto the nearest ruled horizontal, or left alone when the ruling rules none. */
        fun snapY(y: Double): Double =
            if (stepY > 0.0) phaseY + round((y - phaseY) / stepY) * stepY else y

        /**
         * ([x], [y]) pulled onto the nearest point the ruling really draws: the crossing of the
         * ruled lines on each axis it covers, or — for an interleaved [mesh] — the nearest **node**
         * of the two interleaved sub-lattices.
         */
        fun snap(x: Double, y: Double): Pair<Double, Double> {
            val m = mesh ?: return snapX(x) to snapY(y)
            val a = m.nearest(x, y)
            val b = m.nearest(x, y, m.offsetX, m.offsetY)
            val da = (x - a.first) * (x - a.first) + (y - a.second) * (y - a.second)
            val db = (x - b.first) * (x - b.first) + (y - b.second) * (y - b.second)
            return if (da <= db) a else b
        }

        companion object {
            /** A sheet that rules nothing (plain, a PDF/image page, no background): snaps nothing. */
            val NONE = Lattice(0.0, 0.0, 0.0, 0.0)
        }
    }

    /**
     * A **node mesh**: a rectangular lattice of [stepX] × [stepY] pt about ([phaseX], [phaseY]) plus a
     * second copy of it shifted by ([offsetX], [offsetY]). Interleaving the two copies is what turns a
     * rectangular lattice into the **triangular** one a ruling like an isometric sheet rules: the
     * nodes of either copy are the lines' crossings, and the two together are every crossing there is.
     *
     * Distances are in pt (document space); the abstraction is pure geometry, so it is unit-testable
     * on the JVM and shared by the guide's ruling snap and the snap-to-grid shape endpoints.
     */
    data class Mesh(
        /** Node-to-node distance on the X axis in pt. */
        val stepX: Double,
        /** Node-to-node distance on the Y axis in pt. */
        val stepY: Double,
        /** X of the mesh's first node in pt. */
        val phaseX: Double,
        /** Y of the mesh's first node in pt. */
        val phaseY: Double,
        /** X offset of the second interleaved sub-lattice in pt. */
        val offsetX: Double,
        /** Y offset of the second interleaved sub-lattice in pt. */
        val offsetY: Double,
    ) {
        /**
         * The node nearest ([x], [y]) on this sub-lattice or on the copy [shiftX]/[shiftY] away
         * from it. A non-positive step leaves that axis alone, which is what keeps a half-ruled mesh
         * from inventing a line.
         */
        fun nearest(x: Double, y: Double, shiftX: Double = 0.0, shiftY: Double = 0.0): Pair<Double, Double> {
            val ox = phaseX + shiftX
            val oy = phaseY + shiftY
            val nx = if (stepX > 0.0) ox + round((x - ox) / stepX) * stepX else x
            val ny = if (stepY > 0.0) oy + round((y - oy) / stepY) * stepY else y
            return nx to ny
        }
    }

    /**
     * [background]'s ruling as a snapping [Lattice]: its spacing, and the phase the renderer actually
     * draws it at. Both this app's snap-to-grid and the drawing guide's ruling snap resolve the paper
     * through here, so a snapped vertex always lands on a line that is really on the page.
     */
    fun lattice(background: Background?, pageWidthPt: Double = 0.0): Lattice {
        val style = styleOf(background) ?: return Lattice.NONE
        if (style == "isograph" || style == "isodotted") return isometricMesh(background, style, pageWidthPt)
        if (style != "lined" && style != "ruled" && style != "graph" && style != "dotted") {
            return Lattice.NONE
        }
        val spacing = spacingPt(background, style)
        if (spacing <= 0.0) return Lattice.NONE
        val twoAxis = style == "graph" || style == "dotted"
        if (!twoAxis) return Lattice(0.0, spacing, 0.0, 0.0)
        val margin = BackgroundRulings.marginPt(
            BackgroundRuling.parse((background as? Background.Solid)?.config),
        ) ?: 0.0
        return Lattice(spacing, spacing, margin, margin)
    }

    /**
     * An **isometric** sheet (desktop's `isograph`/`isodotted`) as a node mesh.
     *
     * The paper rules the two ±30° families of lines that [BackgroundGrid.isometric] draws, and where
     * a line of one family crosses a line of the other there is a node. Taking the families' own
     * equations, the crossings are `x = W/2 + (√3·s/4)·n`, `y = -W/(2√3) - s/2 + (s/4)·m` with `n` and
     * `m` of the **same parity** (`W` the page width, `s` the triangle side = `r1`) — i.e. two copies
     * of a rectangular `√3·s/2 × s/2` mesh whose second copy is shifted by `(√3·s/4, s/4)`.
     *
     * The phase therefore depends on the **page width**, which is why it has to be handed in: without
     * it (no page, or a page of no width) nothing is resolvable and the sheet rules nothing to snap to.
     */
    private fun isometricMesh(background: Background?, style: String, pageWidthPt: Double): Lattice {
        val side = spacingPt(background, style)
        if (side <= 0.0 || pageWidthPt <= 0.0) return Lattice.NONE
        val root3 = sqrt(3.0)
        val halfX = root3 * side / 4.0
        val halfY = side / 4.0
        return Lattice(
            0.0,
            0.0,
            0.0,
            0.0,
            Mesh(
                stepX = 2.0 * halfX,
                stepY = 2.0 * halfY,
                phaseX = pageWidthPt / 2.0,
                phaseY = -pageWidthPt / (2.0 * root3) - side / 2.0,
                offsetX = halfX,
                offsetY = halfY,
            ),
        )
    }

    /**
     * Horizontal ruling spacing (pt) for [background], or 0 when it rules no vertical lines.
     * @return Spacing in points, or 0.0 if no horizontal snapping.
     */
    fun spacingX(background: Background?): Double = lattice(background).stepX

    /**
     * Vertical ruling spacing (pt) for [background], or 0 when it rules no horizontal lines.
     * @return Spacing in points, or 0.0 if no vertical snapping.
     */
    fun spacingY(background: Background?): Double = lattice(background).stepY

    /**
     * The spacing the page actually rules at: its own `<background config=…>` `r1` when it has one,
     * else the style's default. Snapping to a constant while the page draws a custom spacing would
     * pull a shape off the very line the user can see, so the two resolve the ruling the same way.
     */
    private fun spacingPt(background: Background?, style: String): Double =
        BackgroundRulings.spacingPt(style, BackgroundRuling.parse((background as? Background.Solid)?.config))

    /**
     * [v] pulled to the nearest multiple of [spacing]; a non-positive spacing leaves it alone.
     * @param v Value to snap in points.
     * @param spacing Grid spacing in points.
     * @return Snapped value, or [v] if spacing <= 0.
     */
    fun snap(v: Double, spacing: Double): Double =
        if (spacing <= 0.0) v else round(v / spacing) * spacing

    /**
     * [radians] pulled to the nearest multiple of [stepDeg] degrees.
     * @param radians Angle in radians.
     * @param stepDeg Snap step in degrees (default [ROTATION_STEP_DEG]).
     * @return Snapped angle in radians, or original if stepDeg <= 0.
     */
    fun snapAngle(radians: Double, stepDeg: Double = ROTATION_STEP_DEG): Double {
        if (stepDeg <= 0.0) return radians
        val step = stepDeg * PI / 180.0
        return round(radians / step) * step
    }

    private fun styleOf(background: Background?): String? =
        (background as? Background.Solid)?.style
}
