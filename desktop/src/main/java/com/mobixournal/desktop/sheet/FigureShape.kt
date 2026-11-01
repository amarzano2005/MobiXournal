package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.of

import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind

/**
 * The window's answers to a figure's **own** questions — the triangle's variant and its three angles, the
 * trapezoid's variant and its two base angles.
 *
 * A rectangle has nothing to ask: the drag says everything about it. A triangle does not, because the same
 * three points are four different triangles — equilateral, right-angled, isosceles, scalene — and a
 * trapezoid's legs are fixed by its base angles. The tablet keeps those answers in its Figures settings; the
 * window keeps them here, as one value the drag reads, so the geometry it draws and the page it writes
 * cannot disagree about which triangle is in hand.
 *
 * The polygon's side count is the same kind of answer ([ShapeBuilder.MIN_POLYGON_SIDES]..
 * [ShapeBuilder.MAX_POLYGON_SIDES]): it is not said by the drag either, and the tablet sets it where the
 * figure is drawn. It is held here, next to the variants, so every figure tool reads one value.
 *
 * The defaults are the tablet's own ([TriangleKind.EQUILATERAL], [TrapezoidKind.ISOSCELES], 40°/60°/80°,
 * 75°/60° and a six-sided polygon), so a fresh install draws the same figure on both shells.
 */
data class FigureShape(
    val triangleKind: TriangleKind = TriangleKind.EQUILATERAL,
    /** Interior angles for [TriangleKind.SCALENE], in degrees. */
    val scaleneAngleA: Double = 40.0,
    val scaleneAngleB: Double = 60.0,
    val scaleneAngleC: Double = 80.0,
    val trapezoidKind: TrapezoidKind = TrapezoidKind.ISOSCELES,
    /** The two base angles for [TrapezoidKind.SCALENE], in degrees. */
    val trapezoidAngleA: Double = 75.0,
    val trapezoidAngleB: Double = 60.0,
    /** The sides a [com.mobixournal.render.ShapeKind.POLYGON] drag draws, held to the builder's range. */
    val polygonSides: Int = ShapeBuilder.DEFAULT_POLYGON_SIDES,
) {

    /** This shape with [sides] as the polygon's count, clamped to the builder's own range. */
    fun withPolygonSides(sides: Int): FigureShape =
        copy(polygonSides = sides.coerceIn(ShapeBuilder.MIN_POLYGON_SIDES, ShapeBuilder.MAX_POLYGON_SIDES))
}
