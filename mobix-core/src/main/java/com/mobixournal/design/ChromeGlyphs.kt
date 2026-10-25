package com.mobixournal.design

/**
 * The **chrome's** glyphs — the tablet's own pictures of its commands, as path data on the 24-unit grid.
 *
 * The phone's header and overflow menu draw Material's outlined set (`Icons.Outlined.Save`,
 * `Icons.Outlined.PictureAsPdf`, `Icons.AutoMirrored.Outlined.Undo`, …). A window that re-drew those
 * commands by hand drew *nearly* the same pictures — a floppy that was a page with a notch, a PDF mark
 * that was a page of lines — and a product shown on two screens cannot afford "nearly": the person sees
 * them side by side. So the phone's shapes are **transcribed here once**, as the `d` strings Material
 * publishes (the outlined variants of `note_add`, `file_open`, `save`, `save_as`, `picture_as_pdf`,
 * `image`, `menu`, `undo` and `redo`), and the window fills them.
 *
 * This is the same move the tool catalogue made (`ToolGlyphs.kt`): the art has one home, expressed in
 * plain data with no Compose in it, so a shell draws it with whatever it has — the tablet an
 * `ImageVector`, the desktop a filled `Path` (`SvgPath.kt` parses these strings). No colour is named
 * here: the ink arrives from the shell's theme.
 *
 * The paths are **filled** shapes, not strokes: Material's outlined icons are solid areas with their
 * holes wound the other way, which is why the desktop fills them rather than stroking them.
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
    }
}
