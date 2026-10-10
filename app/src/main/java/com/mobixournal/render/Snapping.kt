package com.mobixournal.render

import com.mobixournal.format.model.Background
import kotlin.math.PI
import kotlin.math.round

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
    ) {
        /** True when this ruling constrains at least one axis. */
        val active: Boolean get() = stepX > 0.0 || stepY > 0.0

        /** [x] pulled onto the nearest ruled vertical, or left alone when the ruling rules none. */
        fun snapX(x: Double): Double =
            if (stepX > 0.0) phaseX + round((x - phaseX) / stepX) * stepX else x

        /** [y] pulled onto the nearest ruled horizontal, or left alone when the ruling rules none. */
        fun snapY(y: Double): Double =
            if (stepY > 0.0) phaseY + round((y - phaseY) / stepY) * stepY else y

        /** ([x], [y]) pulled onto the nearest ruled line/node of each axis the ruling covers. */
        fun snap(x: Double, y: Double): Pair<Double, Double> = snapX(x) to snapY(y)

        companion object {
            /** A sheet that rules nothing (plain, a PDF/image page, no background): snaps nothing. */
            val NONE = Lattice(0.0, 0.0, 0.0, 0.0)
        }
    }

    /**
     * [background]'s ruling as a snapping [Lattice]: its spacing, and the phase the renderer actually
     * draws it at. Both this app's snap-to-grid and the drawing guide's ruling snap resolve the paper
     * through here, so a snapped vertex always lands on a line that is really on the page.
     */
    fun lattice(background: Background?): Lattice {
        val style = styleOf(background) ?: return Lattice.NONE
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
