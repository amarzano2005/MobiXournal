package com.mobixournal.render

/**
 * Geometric variant of the trapezoid tool — a quadrilateral with one pair of parallel sides.
 *
 * - [ISOSCELES]: the two legs equal, so the shorter base sits centred over the longer one (factory
 *   default). Its base angles are equal, and it is symmetric about the vertical axis.
 * - [RIGHT]: one leg perpendicular to the bases (a right trapezoid), so two of its angles are right
 *   angles; the perpendicular leg runs along the drag's left edge.
 * - [SCALENE]: all four sides different, from two customizable base angles [angleA] and [angleB] —
 *   the trapezoid's own analogue of the scalene triangle's angle dialog. The parallel sides are
 *   horizontal, so the figure is built with the given base angles and scaled to fit its drag.
 */
enum class TrapezoidKind(val label: String) {
    ISOSCELES("Isosceles"),
    RIGHT("Right-angled"),
    SCALENE("Scalene"),
}
