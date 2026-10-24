package com.mobixournal.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.mobixournal.design.ToolGlyph
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind

/**
 * The **variants** of the triangle and trapezoid tools, mapped onto the shared catalogue.
 *
 * The rail shows the *chosen* figure on the slot face: pick a right-angled triangle and the slot
 * draws a right-angled one. Those seven pictures used to be a second set of hand-built vectors in this
 * file, which is how the window and the tablet ended up able to disagree about a tool they both show.
 * They are catalogue entries now (`ToolGlyph.TRIANGLE_RIGHT` and its siblings), so the adapters below
 * are the whole of what this file still does: turn a kind into the glyph, and the glyph into the
 * tablet's `ImageVector` (`ToolGlyph.imageVector`, `ToolGlyphIcons.kt`).
 *
 * A variant is recognisable by its **proportions** — wide and short, tall and narrow, standing on a
 * vertical leg, leaning off-centre — because at 18 dp the silhouette is all a user sees.
 */

/** The catalogue glyph that shows [kind] on a slot face or a picker row. */
fun triangleGlyph(kind: TriangleKind): ToolGlyph = when (kind) {
    TriangleKind.EQUILATERAL -> ToolGlyph.TRIANGLE_EQUILATERAL
    TriangleKind.ISOSCELES -> ToolGlyph.TRIANGLE_ISOSCELES
    TriangleKind.RIGHT -> ToolGlyph.TRIANGLE_RIGHT
    TriangleKind.SCALENE -> ToolGlyph.TRIANGLE_SCALENE
}

/** The catalogue glyph that shows [kind] on a slot face or a picker row. */
fun trapezoidGlyph(kind: TrapezoidKind): ToolGlyph = when (kind) {
    TrapezoidKind.ISOSCELES -> ToolGlyph.TRAPEZOID
    TrapezoidKind.RIGHT -> ToolGlyph.TRAPEZOID_RIGHT
    TrapezoidKind.SCALENE -> ToolGlyph.TRAPEZOID_SCALENE
}

/** The outline that represents [kind] beside its name in the picker and in Settings. */
internal fun triangleKindIcon(kind: TriangleKind): ImageVector = triangleGlyph(kind).imageVector()

/** The outline that represents [kind] beside its name in the picker and in Settings. */
internal fun trapezoidKindIcon(kind: TrapezoidKind): ImageVector = trapezoidGlyph(kind).imageVector()

/**
 * The aromatic ring the chemistry guides show.
 *
 * A guide is not an `EditorTool`, so it is not a slot face and the tool catalogue does not have to own
 * it — but the ring is already there as `BENZENE`, so the guides draw that entry rather than keeping a
 * second copy of the same hexagon in the app.
 */
val benzeneGlyph: ToolGlyph = ToolGlyph.BENZENE

/** The same entry as an `ImageVector`, for the guide list. */
val BenzeneIcon: ImageVector get() = benzeneGlyph.imageVector()
