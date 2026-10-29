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
 * A mark has to be recognised **at 16 dp**, the size an index row draws it, so each one is a silhouette
 * with one idea in it: a pen, a sheet, a drop. An abstract diagram — the old nib (a stick over a
 * triangle) and the old tap (a ring inside a ring) — reads as a funnel and a target at that size, which
 * is why [STYLUS] now wears the toolbar's own pen and [GESTURES] a fingertip on the glass.
 *
 * Coordinates are on the **24-unit icon grid** and the stroke is
 * [DesignTokens.TOOL_GLYPH_STROKE_UNITS] of it, the same grid and weight as the tool and chrome art, so a
 * glyph in the settings index reads as an icon of the same family at 20 dp. Nothing here names a colour.
 */
enum class SettingsGlyph {
    /** The pen: pressure, buttons, feel. Wears the toolbar's pen, so the product has one pen picture. */
    STYLUS,

    /** Editing behaviour: default tool, snapping, recognition. */
    EDITOR,

    /** Where the tool strips stand and how they are arranged. */
    TOOLBAR,

    /** Figures and shapes: sizes and defaults. */
    FIGURES,

    /** The palette: the pen's colour and the favourites. */
    COLORS,

    /** Finger and pen gestures: a fingertip touching the page. */
    GESTURES,

    /** The key table. */
    SHORTCUTS,

    /** The microphone and the output: what the window records and plays through. */
    AUDIO,

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
        SettingsGlyph.STYLUS -> pen()
        SettingsGlyph.EDITOR -> sliders()
        SettingsGlyph.TOOLBAR -> toolbar()
        SettingsGlyph.FIGURES -> figures()
        SettingsGlyph.COLORS -> droplet()
        SettingsGlyph.GESTURES -> tap()
        SettingsGlyph.SHORTCUTS -> keyboard()
        SettingsGlyph.AUDIO -> microphone()
        SettingsGlyph.NAVIGATION -> move()
        SettingsGlyph.APPEARANCE -> contrast()
        SettingsGlyph.DOCUMENTS -> page()
        SettingsGlyph.STORAGE -> cylinder()
        SettingsGlyph.BACKUP -> restore()
        SettingsGlyph.ABOUT -> info()
    }

    // --- the art ---------------------------------------------------------------------------------

    /**
     * The pen — and it is literally the **pen tool's own drawing**, taken from [ToolGlyphCatalogue].
     *
     * The first version was a stick standing on a triangle: a nib drawn as a diagram. At 16 dp nobody
     * read a pen in it, and a settings mark whose picture is not the tool it configures is worse than no
     * mark at all. The pencil the rail already shows for *Pen* is a silhouette people know, and reusing
     * it means the product has one pen picture instead of two that can drift apart.
     */
    private fun pen(): List<GlyphPart> = ToolGlyphCatalogue.parts(ToolGlyph.PEN)

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

    /**
     * A drop of ink: the palette's own mark, with the round belly a drop actually has.
     *
     * Two bowed curves meeting at a point — the first version — is a **lens**: the sides pulled in to a
     * sliver and the bottom pointed, which at 16 dp reads as an eye, not as ink. Four curves give it a
     * belly that holds the space inside the mark and a waist it falls from.
     */
    private fun droplet(): List<GlyphPart> = listOf(
        GlyphPart.Cubic(12f, 3.2f, 8.8f, 7.2f, 6.2f, 10.4f, 6.2f, 13.4f),
        GlyphPart.Cubic(6.2f, 13.4f, 6.2f, 17.2f, 8.6f, 20.4f, 12f, 20.4f),
        GlyphPart.Cubic(12f, 20.4f, 15.4f, 20.4f, 17.8f, 17.2f, 17.8f, 13.4f),
        GlyphPart.Cubic(17.8f, 13.4f, 17.8f, 10.4f, 15.2f, 7.2f, 12f, 3.2f),
    )

    /**
     * Two fingertips coming down onto the page: the pair of fingers the section's taps are made with.
     *
     * Two concentric rings over a rule — the first version — is a target, or a record button: a ring says
     * *aim*, not *finger*. One fingertip alone read as a post on a plinth, so the mark carries the two
     * fingers the taps are actually made with, at slightly different lengths the way a hand's fingers are,
     * on the glass they land on. Nothing else is added: ripples and motion arcs merge into a smudge at
     * 16 dp.
     */
    private fun tap(): List<GlyphPart> = listOf(
        // The index finger: the longer one, drawn as a rounded tip open at the bottom.
        GlyphPart.Line(6.4f, 16.4f, 6.4f, 7.6f),
        GlyphPart.Cubic(6.4f, 7.6f, 6.4f, 3.8f, 10.6f, 3.8f, 10.6f, 7.6f),
        GlyphPart.Line(10.6f, 7.6f, 10.6f, 16.4f),
        // The second finger, a little shorter and clearly apart from the first, so the pair reads as two
        // fingers on the glass and not as one arch with a notch.
        GlyphPart.Line(13.4f, 16.4f, 13.4f, 9.2f),
        GlyphPart.Cubic(13.4f, 9.2f, 13.4f, 5.4f, 17.6f, 5.4f, 17.6f, 9.2f),
        GlyphPart.Line(17.6f, 9.2f, 17.6f, 16.4f),
        // The page under them, so the tips are *touching* rather than floating.
        GlyphPart.Line(3.2f, 19.6f, 20.8f, 19.6f),
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

    /**
     * A microphone: the body, its cradle and the stand — the mark the header's capsule wears when a take
     * is not running, so the settings page for it shows the same picture the button does.
     */
    private fun microphone(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(9.2f, 4.6f), GlyphPoint(9.2f, 11.6f), GlyphPoint(14.8f, 11.6f), GlyphPoint(14.8f, 4.6f),
            ),
            closed = true,
        ),
        GlyphPart.Cubic(6.4f, 10f, 6.4f, 17.4f, 17.6f, 17.4f, 17.6f, 10f),
        GlyphPart.Line(12f, 17.4f, 12f, 20.4f),
        GlyphPart.Line(8.6f, 20.4f, 15.4f, 20.4f),
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

    /**
     * An arrow **down** into a tray: putting a copy somewhere safe.
     *
     * The arrow used to point up, out of the tray — which is *export*, the opposite of what this section
     * does, and one picture away from the app's own PDF/SVG arrows. Down into the tray is the recovery
     * snapshot: the work goes in, and comes back out when it is needed.
     */
    private fun restore(): List<GlyphPart> = listOf(
        GlyphPart.Line(12f, 3.4f, 12f, 13.6f),
        GlyphPart.Line(8.4f, 10f, 12f, 13.6f),
        GlyphPart.Line(15.6f, 10f, 12f, 13.6f),
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
