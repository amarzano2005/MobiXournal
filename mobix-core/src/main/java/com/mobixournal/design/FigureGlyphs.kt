package com.mobixournal.design

import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind

/**
 * The **variants** of the triangle and trapezoid tools, as catalogue entries.
 *
 * A variant is recognisable by its **proportions** — wide and short, tall and narrow, standing on a
 * vertical leg, leaning off-centre — because at 18 dp the silhouette is all a user sees. The art lives
 * in [ToolGlyphCatalogue] (`TRIANGLE_RIGHT` and its siblings); this file only answers *which* entry
 * shows a kind, so the tablet's rail and the window's figure slots cannot picture one kind two ways.
 */
val TriangleKind.toolGlyph: ToolGlyph
    get() = when (this) {
        TriangleKind.EQUILATERAL -> ToolGlyph.TRIANGLE_EQUILATERAL
        TriangleKind.ISOSCELES -> ToolGlyph.TRIANGLE_ISOSCELES
        TriangleKind.RIGHT -> ToolGlyph.TRIANGLE_RIGHT
        TriangleKind.SCALENE -> ToolGlyph.TRIANGLE_SCALENE
    }

/** The catalogue glyph that shows [this] trapezoid kind on a slot face or a picker cell. */
val TrapezoidKind.toolGlyph: ToolGlyph
    get() = when (this) {
        TrapezoidKind.ISOSCELES -> ToolGlyph.TRAPEZOID
        TrapezoidKind.RIGHT -> ToolGlyph.TRAPEZOID_RIGHT
        TrapezoidKind.SCALENE -> ToolGlyph.TRAPEZOID_SCALENE
    }
