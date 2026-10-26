package com.mobixournal.design

/**
 * The **one catalogue of settings glyphs**, as plain geometry — no Compose, no platform.
 *
 * Both shells draw their settings from this file: the tablet builds an `ImageVector` from the parts, the
 * window fills them into a `DrawScope`, exactly as [ToolGlyphCatalogue] does for the toolbars. The phone
 * used to take a Material `Icons.Outlined` per section and the window had no glyphs at all, so the same
 * section was a different picture — or no picture — on the two shells (`docs/interface-contract.md` §4,
 * §9).
 *
 * One glyph per **settings section** the product has, named after it, so a section's mark is a property
 * of the section rather than a call site's taste: [STYLUS] for the pen, [TOOLBAR] for where the strips
 * stand, [APPEARANCE] for the theme and the status bar's readouts. The two shells' section lists are the
 * same list, so the same thirteen marks serve both.
 *
 * Coordinates are on the **24-unit icon grid** and the stroke is
 * [DesignTokens.TOOL_GLYPH_STROKE_UNITS] of it, the same grid and weight as the tool and chrome art, so a
 * glyph in the settings index reads as an icon of the same family at 20 dp. Nothing here names a colour.
 */
enum class SettingsGlyph {
    /** The pen: pressure, buttons, feel. */
    STYLUS,

    /** Editing behaviour: default tool, snapping, recognition. */
    EDITOR,

    /** Where the tool strips stand and how they are arranged. */
    TOOLBAR,

    /** Figures and shapes: sizes and defaults. */
    FIGURES,

    /** The palette: the pen's colour and the favourites. */
    COLORS,

    /** Finger and pen gestures. */
    GESTURES,

    /** The key table. */
    SHORTCUTS,

    /** Moving around a page: zoom, panning, momentum. */
    NAVIGATION,

    /** How the app looks: the theme, the status bar's readouts. */
    APPEARANCE,

    /** What a document is: its format, its default page. */
    DOCUMENTS,

    /** Where its bytes go: cache and storage. */
    STORAGE,

    /** What keeps work safe: the recovery snapshot. */
    BACKUP,

    /** The app itself: version, formats, licences. */
    ABOUT,
}

/** The catalogue: one glyph, its parts, in draw order. */
object SettingsGlyphCatalogue {

    /** Every part of [glyph], on the 24-unit grid. */
    fun parts(glyph: SettingsGlyph): List<GlyphPart> = when (glyph) {
        SettingsGlyph.STYLUS -> nib()
        SettingsGlyph.EDITOR -> sliders()
        SettingsGlyph.TOOLBAR -> toolbar()
        SettingsGlyph.FIGURES -> figures()
        SettingsGlyph.COLORS -> droplet()
        SettingsGlyph.GESTURES -> tap()
        SettingsGlyph.SHORTCUTS -> keyboard()
        SettingsGlyph.NAVIGATION -> move()
        SettingsGlyph.APPEARANCE -> contrast()
        SettingsGlyph.DOCUMENTS -> page()
        SettingsGlyph.STORAGE -> cylinder()
        SettingsGlyph.BACKUP -> restore()
        SettingsGlyph.ABOUT -> info()
    }

    // --- the art ---------------------------------------------------------------------------------

    /** A stylus nib pointing down: the body, the tip's two shoulders, and the band the grip sits on. */
    private fun nib(): List<GlyphPart> = listOf(
        GlyphPart.Line(12f, 3.5f, 12f, 12.5f),
        GlyphPart.Line(9.5f, 8.5f, 14.5f, 8.5f),
        GlyphPart.Polyline(listOf(GlyphPoint(7.5f, 12.5f), GlyphPoint(12f, 20.5f), GlyphPoint(16.5f, 12.5f)), closed = true),
    )

    /** Three rules with a knob each: a setting you slide. */
    private fun sliders(): List<GlyphPart> = listOf(
        GlyphPart.Line(3.5f, 7f, 20.5f, 7f),
        GlyphPart.Circle(8f, 7f, 1.9f),
        GlyphPart.Line(3.5f, 12f, 20.5f, 12f),
        GlyphPart.Circle(15.5f, 12f, 1.9f),
        GlyphPart.Line(3.5f, 17f, 20.5f, 17f),
        GlyphPart.Circle(10f, 17f, 1.9f),
    )

    /** A window with its tool lane down the left edge, and one slot in the lane. */
    private fun toolbar(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(GlyphPoint(4f, 4.5f), GlyphPoint(20f, 4.5f), GlyphPoint(20f, 19.5f), GlyphPoint(4f, 19.5f)),
            closed = true,
        ),
        GlyphPart.Line(9f, 4.5f, 9f, 19.5f),
        GlyphPart.Line(6.5f, 9f, 6.5f, 15f),
    )

    /** A circle, a square and a triangle: the figures a page can carry. */
    private fun figures(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(GlyphPoint(3.5f, 4f), GlyphPoint(10f, 4f), GlyphPoint(10f, 10.5f), GlyphPoint(3.5f, 10.5f)),
            closed = true,
        ),
        GlyphPart.Circle(16.5f, 7.5f, 3.6f),
        GlyphPart.Polyline(
            listOf(GlyphPoint(4.5f, 20.5f), GlyphPoint(11f, 20.5f), GlyphPoint(7.75f, 14.5f)),
            closed = true,
        ),
    )

    /** A drop of ink: the palette's own mark. */
    private fun droplet(): List<GlyphPart> = listOf(
        GlyphPart.Quad(12f, 3.5f, 5.5f, 12f, 12f, 20.5f),
        GlyphPart.Quad(12f, 20.5f, 18.5f, 12f, 12f, 3.5f),
    )

    /** A tap: two ripples and the surface they land on. */
    private fun tap(): List<GlyphPart> = listOf(
        GlyphPart.Circle(12f, 11.5f, 2.4f),
        GlyphPart.Circle(12f, 11.5f, 6.4f),
        GlyphPart.Line(4.5f, 20f, 19.5f, 20f),
    )

    /** A keyboard: its frame, a row of keys and the space bar. */
    private fun keyboard(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(GlyphPoint(3.5f, 7f), GlyphPoint(20.5f, 7f), GlyphPoint(20.5f, 17f), GlyphPoint(3.5f, 17f)),
            closed = true,
        ),
        GlyphPart.Circle(7.5f, 11f, 0.9f),
        GlyphPart.Circle(12f, 11f, 0.9f),
        GlyphPart.Circle(16.5f, 11f, 0.9f),
        GlyphPart.Line(8.5f, 14.5f, 15.5f, 14.5f),
    )

    /** Four arrowed directions from one centre: moving around the page. */
    private fun move(): List<GlyphPart> = listOf(
        GlyphPart.Line(12f, 3.5f, 12f, 20.5f),
        GlyphPart.Line(12f, 3.5f, 9.5f, 6.5f),
        GlyphPart.Line(12f, 3.5f, 14.5f, 6.5f),
        GlyphPart.Line(12f, 20.5f, 9.5f, 17.5f),
        GlyphPart.Line(12f, 20.5f, 14.5f, 17.5f),
        GlyphPart.Line(3.5f, 12f, 20.5f, 12f),
        GlyphPart.Line(3.5f, 12f, 6.5f, 9.5f),
        GlyphPart.Line(3.5f, 12f, 6.5f, 14.5f),
        GlyphPart.Line(20.5f, 12f, 17.5f, 9.5f),
        GlyphPart.Line(20.5f, 12f, 17.5f, 14.5f),
    )

    /** A disc split down the middle: light on one side, dark on the other. */
    private fun contrast(): List<GlyphPart> = listOf(
        GlyphPart.Circle(12f, 12f, 8.5f),
        GlyphPart.Line(12f, 3.5f, 12f, 20.5f),
        GlyphPart.Cubic(12f, 3.5f, 17f, 7f, 17f, 17f, 12f, 20.5f),
    )

    /** A sheet with a folded corner and two lines of text. */
    private fun page(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(6f, 3.5f), GlyphPoint(15f, 3.5f), GlyphPoint(18.5f, 7f),
                GlyphPoint(18.5f, 20.5f), GlyphPoint(6f, 20.5f),
            ),
            closed = true,
        ),
        GlyphPart.Line(15f, 3.5f, 15f, 7f),
        GlyphPart.Line(15f, 7f, 18.5f, 7f),
        GlyphPart.Line(9f, 12f, 15.5f, 12f),
        GlyphPart.Line(9f, 15.5f, 15.5f, 15.5f),
    )

    /** A cylinder: a store of bytes. */
    private fun cylinder(): List<GlyphPart> = listOf(
        GlyphPart.Line(5f, 7f, 5f, 17.5f),
        GlyphPart.Line(19f, 7f, 19f, 17.5f),
        GlyphPart.Quad(5f, 7f, 12f, 3.2f, 19f, 7f),
        GlyphPart.Quad(5f, 12.5f, 12f, 16.3f, 19f, 12.5f),
        GlyphPart.Quad(5f, 17.5f, 12f, 21.3f, 19f, 17.5f),
    )

    /** An arrow into a tray: putting a copy somewhere safe. */
    private fun restore(): List<GlyphPart> = listOf(
        GlyphPart.Line(12f, 3.5f, 12f, 13.5f),
        GlyphPart.Line(8.5f, 7f, 12f, 3.5f),
        GlyphPart.Line(15.5f, 7f, 12f, 3.5f),
        GlyphPart.Polyline(
            listOf(GlyphPoint(4.5f, 14.5f), GlyphPoint(4.5f, 20.5f), GlyphPoint(19.5f, 20.5f), GlyphPoint(19.5f, 14.5f)),
        ),
    )

    /** The information disc: a ring, its stem and its dot. */
    private fun info(): List<GlyphPart> = listOf(
        GlyphPart.Circle(12f, 12f, 8.5f),
        GlyphPart.Line(12f, 11f, 12f, 16.5f),
        GlyphPart.Circle(12f, 7.8f, 0.9f),
    )

}
