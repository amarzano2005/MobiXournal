package com.mobixournal.desktop

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
 * The defaults are the tablet's own ([TriangleKind.EQUILATERAL], [TrapezoidKind.ISOSCELES], 40°/60°/80° and
 * 75°/60°), so a fresh install draws the same figure on both shells.
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
)
