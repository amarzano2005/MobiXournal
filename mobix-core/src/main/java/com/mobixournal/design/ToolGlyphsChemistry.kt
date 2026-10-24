package com.mobixournal.design

/**
 * The **chemistry** half of the tool-glyph catalogue: the three bond orders a STEM picker offers.
 *
 * The tablet drew these on this same grid at the same weight; they are transcribed here so the
 * catalogue owns every bond order's picture, and that file has been deleted. The aromatic ring is
 * `ToolGlyph.BENZENE`, not a bond — it was already in the catalogue, and the chemistry guide popup
 * draws that entry too.
 */

/** A single bond: one line, tilted 60°. */
internal fun bondSingle(): List<GlyphPart> = listOf(GlyphPart.Line(4.5f, 19.5f, 19.5f, 10.84f))

/** A double bond: two parallel lines, tilted 60°. */
internal fun bondDouble(): List<GlyphPart> = listOf(
    GlyphPart.Line(3f, 17f, 18f, 8.34f),
    GlyphPart.Line(6f, 22f, 21f, 13.34f),
)

/** A triple bond: three parallel lines, tilted 60°. */
internal fun bondTriple(): List<GlyphPart> = listOf(
    GlyphPart.Line(2f, 14f, 15f, 6.49f),
    GlyphPart.Line(5f, 19f, 19f, 10.92f),
    GlyphPart.Line(9f, 23f, 22f, 15.49f),
)
