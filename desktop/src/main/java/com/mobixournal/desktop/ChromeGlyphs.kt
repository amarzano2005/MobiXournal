package com.mobixournal.desktop

import com.mobixournal.design.GlyphPart
import com.mobixournal.design.GlyphPoint

/**
 * The **chrome's** glyphs — undo, redo, save, open, the two exports — on the same 24-unit grid and at
 * the same 1.5-unit stroke as the tool catalogue (`docs/interface-contract.md` §4).
 *
 * The tool catalogue is the tablet's art for *tools*, and a header is not a toolbar: nothing on the
 * tablet's chrome is a `ToolGlyph`. What the window can do is keep the catalogue's **grammar** rather
 * than its contents — the same grid, the same stroke, the same declarative parts — so a header glyph and
 * a rail glyph look like one hand drew them. The parts are plain geometry, which is what lets
 * `ChromeGlyphsTest` check them without a window: every glyph has art, no two glyphs are the same
 * picture, and none of them is a blob in a corner of its grid.
 *
 * The pictures are the mobile bar's, ported: the arrows of its undo and redo, its floppy for Save, its
 * page for a new note, its folder for Open, and the two page marks its export pills wear.
 */
internal enum class ChromeGlyph {
    /** The turn that takes a step back — the mobile bar's undo arrow. */
    UNDO,

    /** And the same turn read the other way. */
    REDO,

    /** A new page: the mobile bar's page with a plus. */
    NEW,

    /** Open: the folder the mobile bar shows. */
    OPEN,

    /** A floppy — the mobile bar's Save. */
    SAVE,

    /** The floppy with a pen: Save As. */
    SAVE_AS,

    /** A page of words — the mark the export-to-PDF action wears. */
    PDF,

    /** A page of picture — the mark the export-to-SVG action wears. */
    PICTURE,

    /** The overflow menu's three lines — the mobile bar's own hamburger. */
    MENU,
}

/**
 * The strokes of [glyph], in the catalogue's vocabulary: a run of points, a single line, a circle, a
 * curve. Read by the same adapter the tool glyphs use ([drawGlyphParts]), so one grid and one stroke
 * width are shared by the whole window.
 */
internal fun chromeGlyphParts(glyph: ChromeGlyph): List<GlyphPart> = when (glyph) {
    ChromeGlyph.UNDO -> TURN
    // The same turn, read the other way: the coordinates are the catalogue's, mirrored in the grid.
    ChromeGlyph.REDO -> TURN.map { it.mirrored() }
    ChromeGlyph.NEW -> listOf(
        page(x = 9f, y = 3f),
        GlyphPart.Line(15f, 3f, 15f, 7f),
        GlyphPart.Line(15f, 7f, 19f, 7f),
        GlyphPart.Line(1.5f, 9f, 6.5f, 9f),
        GlyphPart.Line(4f, 6.5f, 4f, 11.5f),
    )
    ChromeGlyph.OPEN -> listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(2f, 7f), GlyphPoint(2f, 19f), GlyphPoint(22f, 19f),
                GlyphPoint(22f, 9f), GlyphPoint(12f, 9f), GlyphPoint(10f, 7f),
            ),
            closed = true,
        ),
    )
    ChromeGlyph.SAVE -> listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(4f, 3f), GlyphPoint(17f, 3f), GlyphPoint(21f, 7f),
                GlyphPoint(21f, 21f), GlyphPoint(4f, 21f),
            ),
            closed = true,
        ),
        GlyphPart.Polyline(
            listOf(GlyphPoint(8f, 3f), GlyphPoint(8f, 9f), GlyphPoint(16f, 9f), GlyphPoint(16f, 3f)),
        ),
        GlyphPart.Line(8f, 16f, 17f, 16f),
    )
    ChromeGlyph.SAVE_AS -> listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(3f, 3f), GlyphPoint(14f, 3f), GlyphPoint(18f, 7f),
                GlyphPoint(18f, 13f), GlyphPoint(3f, 13f),
            ),
            closed = true,
        ),
        GlyphPart.Polyline(
            listOf(GlyphPoint(6f, 3f), GlyphPoint(6f, 8f), GlyphPoint(13f, 8f), GlyphPoint(13f, 3f)),
        ),
        // The pen laid over the floppy's lower corner, which is the whole difference between the two.
        GlyphPart.Line(11f, 21f, 19.5f, 12.5f),
        GlyphPart.Polyline(listOf(GlyphPoint(11f, 21f), GlyphPoint(10.6f, 17.6f), GlyphPoint(15f, 18f)), closed = true),
    )
    ChromeGlyph.PDF -> listOf(
        page(x = 5f, y = 2f),
        GlyphPart.Line(8.5f, 12f, 15.5f, 12f),
        GlyphPart.Line(8.5f, 16f, 15.5f, 16f),
        GlyphPart.Line(8.5f, 20f, 12f, 20f),
    )
    ChromeGlyph.MENU -> listOf(
        GlyphPart.Line(3f, 6f, 21f, 6f),
        GlyphPart.Line(3f, 12f, 21f, 12f),
        GlyphPart.Line(3f, 18f, 21f, 18f),
    )
    ChromeGlyph.PICTURE -> listOf(
        page(x = 5f, y = 2f),
        GlyphPart.Circle(9.5f, 11.5f, 1.5f),
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(7.5f, 20f), GlyphPoint(11f, 15.5f), GlyphPoint(14f, 18.5f),
                GlyphPoint(16.5f, 16f), GlyphPoint(18f, 20f),
            ),
        ),
    )
}

/** A page whose top-right corner is folded: the outline every file glyph here is built on. */
private fun page(x: Float, y: Float): GlyphPart = GlyphPart.Polyline(
    listOf(
        GlyphPoint(x, y), GlyphPoint(x + 6f, y), GlyphPoint(x + 10f, y + 4f),
        GlyphPoint(x + 10f, y + 18f), GlyphPoint(x, y + 18f),
    ),
    closed = true,
)

/**
 * The undo turn: the whole grid tall, and a U — up one side, over the top, down the other — with the
 * head at the foot it comes back to. That is the shape the mobile bar's arrow has, and the height is
 * what makes it read at 20 dp: a short arc in the top half of the box looks like a smudge.
 */
private val TURN: List<GlyphPart> = listOf(
    GlyphPart.Cubic(x0 = 4.5f, y0 = 18f, cx1 = 4.5f, cy1 = 3f, cx2 = 19.5f, cy2 = 3f, x1 = 19.5f, y1 = 18f),
    GlyphPart.Line(4.5f, 18f, 9f, 13f),
    GlyphPart.Line(4.5f, 18f, 2f, 12.5f),
)

/** The same parts read across the grid's middle: the mirror image of a glyph. */
internal fun GlyphPart.mirrored(): GlyphPart = when (this) {
    is GlyphPart.Line -> GlyphPart.Line(24f - x1, y1, 24f - x2, y2)
    is GlyphPart.Polyline -> GlyphPart.Polyline(points.map { GlyphPoint(24f - it.x, it.y) }, closed)
    is GlyphPart.Circle -> GlyphPart.Circle(24f - cx, cy, radius)
    is GlyphPart.Cubic -> GlyphPart.Cubic(24f - x0, y0, 24f - cx1, cy1, 24f - cx2, cy2, 24f - x1, y1)
    is GlyphPart.Quad -> GlyphPart.Quad(24f - x0, y0, 24f - cx, cy, 24f - x1, y1)
}
