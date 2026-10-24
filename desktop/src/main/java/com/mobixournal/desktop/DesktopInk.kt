package com.mobixournal.desktop

import com.mobixournal.design.PenPalette

/**
 * One tool's ink: the colour and the width it draws at.
 *
 * A **value**, like [ToolSelection]: every change returns the next ink rather than mutating this one, so
 * the shell can hold it in Compose snapshot state and a test can compare two inks without watching for a
 * change notification. Plain Kotlin, no Compose types — the colour is the ARGB the document model uses,
 * not a `Color`, and the width is already the document's unit (points).
 */
data class DesktopInk(
    val colour: Int = PenPalette.COLORS.first(),
    val width: Float = PenPalette.DEFAULT_WIDTHS[1],
) {

    /** The same ink in [colour], promoted to opaque like every palette entry. */
    fun withColour(colour: Int): DesktopInk = copy(colour = PenPalette.opaque(colour))

    /** The same ink at [width], clamped to the range a document may carry. */
    fun withWidth(width: Float): DesktopInk = copy(width = PenPalette.clampWidth(width))
}

/**
 * The window's **two inks**, one per inking tool, exactly as the tablet keeps them.
 *
 * On the phone the pencil and the highlighter each own their colour and width (`EditorUiState.color` /
 * `highlighterColor`, `penWidth` / `highlighterWidth`): dragging a yellow highlighter over a page must not
 * turn the pen yellow, and a restart must bring both tools back the way they were left. One shared ink —
 * what the window had — is a bug you notice the moment you switch tools mid-sentence.
 *
 * The palette itself is **shared** (one set of swatches for both, `AppSettings.penColors`), because a
 * palette is what the user has, not what a tool is. Only what each tool *writes with* is per tool.
 */
data class DesktopInks(
    val pen: DesktopInk = DesktopInk(),
    /**
     * The highlighter's own ink: the tablet's factory colour and its widest slot
     * (`AppSettings.DEFAULT_HIGHLIGHTER_COLOR` = yellow, `DEFAULT_HIGHLIGHTER_WIDTH` = `L`).
     */
    val highlighter: DesktopInk = DesktopInk(
        colour = PenPalette.YELLOW,
        width = PenPalette.DEFAULT_WIDTHS.last(),
    ),
) {

    /** The ink [tool] writes with; every slot that is not the highlighter writes with the pen's. */
    fun forTool(tool: DesktopTool): DesktopInk =
        if (tool == DesktopTool.HIGHLIGHTER) highlighter else pen

    /** The same pair with [tool]'s ink replaced — the other tool's is untouched. */
    fun withInk(tool: DesktopTool, ink: DesktopInk): DesktopInks =
        if (tool == DesktopTool.HIGHLIGHTER) copy(highlighter = ink) else copy(pen = ink)
}

/** What the colour panel will do with the colour it commits. */
enum class ColourEditorTarget {
    /** Write the palette's editable custom slot, and pick it. */
    CUSTOM,

    /** Append a new entry to the palette (opaque, de-duplicated, capped), and pick it. */
    NEW,
}

/**
 * The three favourite colours of each inking tool.
 *
 * The tablet keeps a terna per tool (`AppSettings.penFavorites` / `highlighterFavorites`) because the
 * digits are a *writing* shortcut: `1` under the pen must not mean the highlighter's first colour. The
 * window follows that, so pressing `1` while the highlighter is live takes the highlighter's first
 * favourite rather than the pen's.
 */
data class DesktopFavourites(
    val pen: List<Int> = PenPalette.COLORS.take(PaletteCodec.FAVOURITES),
    val highlighter: List<Int> = PenPalette.COLORS.take(PaletteCodec.FAVOURITES),
) {

    /** The terna [tool] picks from — the pen's for every slot that is not the highlighter. */
    fun forTool(tool: DesktopTool): List<Int> =
        if (tool == DesktopTool.HIGHLIGHTER) highlighter else pen

    /** The same pair with [tool]'s terna replaced. */
    fun withFavourites(tool: DesktopTool, colours: List<Int>): DesktopFavourites =
        if (tool == DesktopTool.HIGHLIGHTER) copy(highlighter = colours) else copy(pen = colours)
}

/**
 * The settings-file spelling of ARGB colours: comma-separated, eight lowercase hex digits each
 * (`ff000000,ffff0000`), which is readable in the file and round-trips every int including the
 * "negative" ones an ARGB colour looks like in Kotlin.
 *
 * Decoding runs the list through [PenPalette.sanitised], so a hand-edited file cannot put a transparent
 * colour, a duplicate or an empty palette in front of the picker.
 */
internal object PaletteCodec {

    /** How many favourite colours the digit keys bind to. */
    const val FAVOURITES: Int = 3

    fun encode(colors: List<Int>): String = colors.joinToString(",") { hex(it) }

    fun decode(raw: String?): List<Int> {
        val parsed = raw?.split(',')?.mapNotNull { entry -> entry.trim().toLongOrNull(16)?.toInt() }
        return if (parsed.isNullOrEmpty()) PenPalette.COLORS else PenPalette.sanitised(parsed)
    }

    /**
     * The three favourite colours: opaque, de-duplicated, and always exactly [FAVOURITES] long — a
     * missing or short entry is filled from the factory palette, so keys 1–3 always have a colour.
     */
    fun decodeFavourites(raw: String?): List<Int> {
        val parsed = raw?.split(',')?.mapNotNull { entry -> entry.trim().toLongOrNull(16)?.toInt() }
            ?.map(PenPalette::opaque)
            ?: emptyList()
        return (parsed + PenPalette.COLORS).distinct().take(FAVOURITES)
    }

    /** One colour as the eight-digit hex the file holds. */
    fun hex(colour: Int): String = colour.toUInt().toString(16).padStart(8, '0')

    /** One colour back from [hex]; null when the text is not eight hex digits. */
    fun parseHex(text: String?): Int? = text?.trim()?.takeIf { it.length <= 8 }?.toLongOrNull(16)?.toInt()
}
