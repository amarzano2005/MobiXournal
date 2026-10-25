package com.mobixournal.design

/**
 * The **chrome's** glyphs — the one catalogue both shells draw their commands from, as path data on the
 * 24-unit grid.
 *
 * The chrome is everything that is not a tool face: the file commands, undo/redo, the overflow button,
 * and the marks that act on a selection, a tab, a layer or a panel (cut, copy, the align family, the
 * close and add marks, the layer eye). It used to be **two** sets — the phone drawing Material's outlined
 * icons, the window drawing its own monoline art — and a product shown on two screens cannot afford
 * "nearly": put the phone beside the PC and the same command wore two different pictures. So the art has
 * one home, here, expressed as plain data with no Compose in it.
 *
 * Two adapters consume it, and nothing else may:
 * - the tablet builds an `ImageVector` from the same string (`ChromeGlyphIcons.kt`, `app`);
 * - the window fills a `Path` parsed from it (`SvgPath.kt`, `desktop`).
 *
 * The paths are the `d` strings Material publishes, **transcribed from the phone's own vectors** rather
 * than retyped: a small generator reads `Icons.Outlined.*` and prints these strings, so a glyph cannot
 * drift into an artist's near-version of itself.
 *
 * They are **filled** shapes, not strokes: Material's outlined icons are solid areas with their holes
 * wound the other way, which is why a shell fills them rather than stroking them. No colour is named
 * here: the ink arrives from the shell's theme (`docs/interface-contract.md` §4).
 */
enum class ChromeGlyph {
    /** A new note: a page with a plus — the tablet's `NoteAdd`. */
    NEW,

    /** Open: a page with the corner turning over — the tablet's `FileOpen`. */
    OPEN,

    /** The floppy the tablet's Save wears. */
    SAVE,

    /** The same floppy with a pen over it: Save As. */
    SAVE_AS,

    /** A sheet of text: Picture as PDF. */
    PDF,

    /** The framed picture: the image and page-raster pictures. */
    PICTURE,

    /** The three lines of the overflow button. */
    MENU,

    /** The turn that takes a step back. */
    UNDO,

    /** And its mirror. */
    REDO,

    /** Dismiss: the tab's own close, a panel's, a card's. */
    CLOSE,

    /** Add: a new page in the strip, a colour in the palette, a row in a lane. */
    ADD,

    /** The layer eye, open. */
    VISIBILITY,

    /** The layer eye, struck through. */
    VISIBILITY_OFF,

    /** Scissors: two blades crossing over a pair of rings. */
    CUT,

    /** Two sheets, one behind the other. */
    COPY,

    /** A clipboard with its clip. */
    PASTE,

    /** A sheet with a plus: the selection, again. */
    DUPLICATE,

    /** The painter's palette: recolour the selection. */
    RECOLOUR,

    /** Four bars of different heights: the stroke's width. */
    WIDTH,

    /** The pencil: edit a living element. */
    EDIT,

    /** The bin: delete the selection. */
    DELETE,

    /** Four lines flush left, with a rule down the left edge. */
    ALIGN_LEFT,

    /** The same lines centred. */
    ALIGN_H_CENTRE,

    /** And flush right. */
    ALIGN_RIGHT,

    /** An arrow up against a rule: align to the top. */
    ALIGN_TOP,

    /** The arrow between two arrows: align to the vertical middle. */
    ALIGN_V_MIDDLE,

    /** The arrow down against a rule: align to the bottom. */
    ALIGN_BOTTOM,

    /** Equal gaps left and right: distribute horizontally. */
    DISTRIBUTE_H,

    /** Equal gaps above and below: distribute vertically. */
    DISTRIBUTE_V,
}

/** The catalogue: one glyph, its path data. */
object ChromeGlyphCatalogue {

    /** The `d` of [glyph], on the 24-unit grid — the shape Material publishes for that command. */
    fun path(glyph: ChromeGlyph): String = when (glyph) {
        ChromeGlyph.NEW ->
            "M13 11h-2v3H8v2h3v3h2v-3h3v-2h-3zm1-9H6c-1.1 0-2 .9-2 2v16c0 1.1.89 2 1.99 2H18c1.1 0 " +
                "2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z"
        ChromeGlyph.OPEN ->
            "M15 22H6c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h8l6 6v6h-2V9h-5V4H6v16h9v2zm4-.34v-2.24l2.95 " +
                "2.95 1.41-1.41L20.41 18h2.24v-2H17v5.66h2z"
        ChromeGlyph.SAVE ->
            "M17 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm2 16H5V5h11.17L19 " +
                "7.83V19zm-7-7c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3zM6 6h9v4H6z"
        ChromeGlyph.SAVE_AS ->
            "M21 12.4V7l-4-4H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h7.4l2-2H5V5h11.17L19 7.83v6.57l2-2zM15 " +
                "15c0 1.66-1.34 3-3 3s-3-1.34-3-3 1.34-3 3-3 3 1.34 3 3zM6 6h9v4H6V6zm13.99 10.25 " +
                "1.77 1.77L16.77 23H15v-1.77l4.99-4.98zm3.26.26-.85.85-1.77-1.77.85-.85c.2-.2.51-.2.71 " +
                "0l1.06 1.06c.2.2.2.52 0 .71z"
        ChromeGlyph.PDF ->
            "M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 " +
                "14H8V4h12v12zm-16-10H2v14c0 1.1.9 2 2 2h14v-2H4V6zm12 6V9c0-.55-.45-1-1-1h-2v5h2c.55 0 " +
                "1-.45 1-1zm-2-3h1v3h-1V9zm4 2h1v-1h-1V9h1V8h-2v5h1zm-8 0h1c.55 0 1-.45 1-1V9c0-.55-.45-1-1-1H9v5h1v-2zm0-2h1v1h-1V9z"
        ChromeGlyph.PICTURE ->
            "M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-4.86 " +
                "8.86-3 3.87L9 13.14 6 17h12l-3.86-5.14z"
        ChromeGlyph.MENU -> "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"
        ChromeGlyph.UNDO ->
            "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 " +
                "6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z"
        ChromeGlyph.REDO ->
            "M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16a8.002 8.002 0 0 " +
                "1 7.6-5.5c1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z"
        ChromeGlyph.CLOSE ->
            "M19 6.41L17.59 5L12 10.59L6.41 5L5 6.41L10.59 12L5 17.59L6.41 19L12 13.41L17.59 19L19 17.59L13.41 12L19 6.41Z"
        ChromeGlyph.ADD -> "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2Z"
        ChromeGlyph.VISIBILITY ->
            "M12 6c3.79 0 7.17 2.13 8.82 5.5C19.17 14.87 15.79 17 12 17s-7.17 -2.13 -8.82 -5.5C4.83 " +
                "8.13 8.21 6 12 6m0 -2C7 4 2.73 7.11 1 11.5C2.73 15.89 7 19 12 19s9.27 -3.11 11 " +
                "-7.5C21.27 7.11 17 4 12 4ZM12 9c1.38 0 2.5 1.12 2.5 2.5S13.38 14 12 14s-2.5 -1.12 -2.5 " +
                "-2.5S10.62 9 12 9m0 -2c-2.48 0 -4.5 2.02 -4.5 4.5S9.52 16 12 16s4.5 -2.02 4.5 -4.5S14.48 7 12 7Z"
        ChromeGlyph.VISIBILITY_OFF ->
            "M12 6c3.79 0 7.17 2.13 8.82 5.5c-0.59 1.22 -1.42 2.27 -2.41 3.12l1.41 1.41c1.39 -1.23 " +
                "2.49 -2.77 3.18 -4.53C21.27 7.11 17 4 12 4c-1.27 0 -2.49 0.2 -3.64 0.57l1.65 " +
                "1.65C10.66 6.09 11.32 6 12 6ZM10.93 7.14L13 9.21c0.57 0.25 1.03 0.71 1.28 1.28l2.07 " +
                "2.07c0.08 -0.34 0.14 -0.7 0.14 -1.07C16.5 9.01 14.48 7 12 7c-0.37 0 -0.72 0.05 -1.07 " +
                "0.14ZM2.01 3.87l2.68 2.68C3.06 7.83 1.77 9.53 1 11.5C2.73 15.89 7 19 12 19c1.52 0 " +
                "2.98 -0.29 4.32 -0.82l3.42 3.42l1.41 -1.41L3.42 2.45L2.01 3.87ZM9.51 11.37l2.61 " +
                "2.61c-0.04 0.01 -0.08 0.02 -0.12 0.02c-1.38 0 -2.5 -1.12 -2.5 -2.5c0 -0.05 0.01 -0.08 " +
                "0.01 -0.13ZM6.11 7.97l1.75 1.75c-0.23 0.55 -0.36 1.15 -0.36 1.78c0 2.48 2.02 4.5 4.5 " +
                "4.5c0.63 0 1.23 -0.13 1.77 -0.36l0.98 0.98c-0.88 0.24 -1.8 0.38 -2.75 0.38c-3.79 0 " +
                "-7.17 -2.13 -8.82 -5.5c0.7 -1.43 1.72 -2.61 2.93 -3.53Z"
        ChromeGlyph.CUT ->
            "M9.64 7.64c0.23 -0.5 0.36 -1.05 0.36 -1.64c0 -2.21 -1.79 -4 -4 -4S2 3.79 2 6s1.79 4 4 " +
                "4c0.59 0 1.14 -0.13 1.64 -0.36L10 12l-2.36 2.36C7.14 14.13 6.59 14 6 14c-2.21 0 -4 " +
                "1.79 -4 4s1.79 4 4 4s4 -1.79 4 -4c0 -0.59 -0.13 -1.14 -0.36 -1.64L12 14l7 7h3v-1L9.64 " +
                "7.64ZM6 8c-1.1 0 -2 -0.89 -2 -2s0.9 -2 2 -2s2 0.89 2 2s-0.9 2 -2 2ZM6 20c-1.1 0 -2 " +
                "-0.89 -2 -2s0.9 -2 2 -2s2 0.89 2 2s-0.9 2 -2 2ZM12 12.5c-0.28 0 -0.5 -0.22 -0.5 -0.5s0.22 " +
                "-0.5 0.5 -0.5s0.5 0.22 0.5 0.5s-0.22 0.5 -0.5 0.5ZM19 3l-6 6l2 2l7 -7L22 3h-3Z"
        ChromeGlyph.COPY ->
            "M16 1L4 1c-1.1 0 -2 0.9 -2 2v14h2L4 3h12L16 1ZM19 5L8 5c-1.1 0 -2 0.9 -2 2v14c0 1.1 0.9 " +
                "2 2 2h11c1.1 0 2 -0.9 2 -2L21 7c0 -1.1 -0.9 -2 -2 -2ZM19 21L8 21L8 7h11v14Z"
        ChromeGlyph.PASTE ->
            "M19 2h-4.18C14.4 0.84 13.3 0 12 0S9.6 0.84 9.18 2L5 2c-1.1 0 -2 0.9 -2 2v16c0 1.1 0.9 " +
                "2 2 2h14c1.1 0 2 -0.9 2 -2L21 4c0 -1.1 -0.9 -2 -2 -2ZM12 2c0.55 0 1 0.45 1 1s-0.45 1 -1 " +
                "1s-1 -0.45 -1 -1s0.45 -1 1 -1ZM19 20L5 20L5 4h2v3h10L17 4h2v16Z"
        ChromeGlyph.DUPLICATE ->
            "M4 6L2 6v14c0 1.1 0.9 2 2 2h14v-2L4 20L4 6ZM20 2L8 2c-1.1 0 -2 0.9 -2 2v12c0 1.1 0.9 2 " +
                "2 2h12c1.1 0 2 -0.9 2 -2L22 4c0 -1.1 -0.9 -2 -2 -2ZM20 16L8 16L8 4h12v12ZM13 14h2v-3h3L18 " +
                "9h-3L15 6h-2v3h-3v2h3Z"
        ChromeGlyph.RECOLOUR ->
            "M12 22C6.49 22 2 17.51 2 12S6.49 2 12 2s10 4.04 10 9c0 3.31 -2.69 6 -6 6h-1.77c-0.28 0 " +
                "-0.5 0.22 -0.5 0.5c0 0.12 0.05 0.23 0.13 0.33c0.41 0.47 0.64 1.06 0.64 " +
                "1.67C14.5 20.88 13.38 22 12 22ZM12 4c-4.41 0 -8 3.59 -8 8s3.59 8 8 8c0.28 0 0.5 -0.22 " +
                "0.5 -0.5c0 -0.16 -0.08 -0.28 -0.14 -0.35c-0.41 -0.46 -0.63 -1.05 -0.63 -1.65c0 -1.38 " +
                "1.12 -2.5 2.5 -2.5H16c2.21 0 4 -1.79 4 -4C20 7.14 16.41 4 12 4ZM6.5 11.5m-1.5 0a1.5 1.5 " +
                "0 1 1 3 0a1.5 1.5 0 1 1 -3 0M9.5 7.5m-1.5 0a1.5 1.5 0 1 1 3 0a1.5 1.5 0 1 1 -3 0M14.5 " +
                "7.5m-1.5 0a1.5 1.5 0 1 1 3 0a1.5 1.5 0 1 1 -3 0M17.5 11.5m-1.5 0a1.5 1.5 0 1 1 3 0a1.5 " +
                "1.5 0 1 1 -3 0"
        ChromeGlyph.WIDTH -> "M3 17h18v-2L3 15v2ZM3 20h18v-1L3 19v1ZM3 13h18v-3L3 10v3ZM3 4v4h18L21 4L3 4Z"
        ChromeGlyph.EDIT ->
            "M14.06 9.02l0.92 0.92L5.92 19L5 19v-0.92l9.06 -9.06M17.66 3c-0.25 0 -0.51 0.1 -0.7 " +
                "0.29l-1.83 1.83l3.75 3.75l1.83 -1.83c0.39 -0.39 0.39 -1.02 0 -1.41l-2.34 -2.34c-0.2 -0.2 " +
                "-0.45 -0.29 -0.71 -0.29ZM14.06 6.19L3 17.25L3 21h3.75L17.81 9.94l-3.75 -3.75Z"
        ChromeGlyph.DELETE ->
            "M16 9v10H8V9h8m-1.5 -6h-5l-1 1H5v2h14V4h-3.5l-1 -1ZM18 7H6v12c0 1.1 0.9 2 2 2h8c1.1 0 " +
                "2 -0.9 2 -2V7Z"
        ChromeGlyph.ALIGN_LEFT ->
            "M15 15L3 15v2h12v-2ZM15 7L3 7v2h12L15 7ZM3 13h18v-2L3 11v2ZM3 21h18v-2L3 19v2ZM3 3v2h18L21 3L3 3Z"
        ChromeGlyph.ALIGN_H_CENTRE ->
            "M7 15v2h10v-2L7 15ZM3 21h18v-2L3 19v2ZM3 13h18v-2L3 11v2ZM7 7v2h10L17 7L7 7ZM3 3v2h18L21 3L3 3Z"
        ChromeGlyph.ALIGN_RIGHT ->
            "M3 21h18v-2L3 19v2ZM9 17h12v-2L9 15v2ZM3 13h18v-2L3 11v2ZM9 9h12L21 7L9 7v2ZM3 3v2h18L21 3L3 3Z"
        ChromeGlyph.ALIGN_TOP -> "M8 11h3v10h2V11h3l-4 -4l-4 4ZM4 3v2h16V3H4Z"
        ChromeGlyph.ALIGN_V_MIDDLE ->
            "M8 19h3v4h2v-4h3l-4 -4l-4 4ZM16 5h-3L13 1h-2v4L8 5l4 4l4 -4ZM4 11v2h16v-2L4 11Z"
        ChromeGlyph.ALIGN_BOTTOM -> "M16 13h-3V3h-2v10H8l4 4l4 -4ZM4 19v2h16v-2H4Z"
        ChromeGlyph.DISTRIBUTE_H -> "M4 22H2V2h2V22ZM22 2h-2v20h2V2ZM13.5 7h-3v10h3V7Z"
        ChromeGlyph.DISTRIBUTE_V -> "M22 2v2H2V2H22ZM7 10.5v3h10v-3H7ZM2 20v2h20v-2H2Z"
    }
}
