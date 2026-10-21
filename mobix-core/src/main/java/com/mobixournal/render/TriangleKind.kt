package com.mobixournal.render

/**
 * Geometric variant of the triangle tool.
 *
 * - [EQUILATERAL]: All three sides equal, 60° interior angles (factory default).
 * - [RIGHT]: One 90° angle oriented along the drag axes.
 * - [ISOSCELES]: Two equal sides, apex centered over base.
 * - [SCALENE]: All three sides and angles different, with customizable angles A, B, and C (sum = 180°).
 */
enum class TriangleKind(val label: String) {
    EQUILATERAL("Equilateral"),
    RIGHT("Right-angled"),
    ISOSCELES("Isosceles"),
    SCALENE("Scalene"),
}
