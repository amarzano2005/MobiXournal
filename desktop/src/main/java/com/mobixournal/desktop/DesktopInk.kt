package com.mobixournal.desktop

import com.mobixournal.design.PenPalette

/**
 * The window's ink: the colour and the width the strips show, plus the palette's editable custom slot.
 *
 * A **value**, like [ToolSelection]: every change returns the next ink rather than mutating this one, so
 * the shell can hold it in Compose snapshot state and a test can compare two inks without watching for a
 * change notification. Plain Kotlin, no Compose types — the colour is the ARGB the document model uses,
 * not a `Color`.
 *
 * Nothing draws with it yet: this is the chrome's state for the editing phase, and the width is already
 * the document's unit (points), the same number `AppSettings.penWidths` carries on the tablet.
 */
data class DesktopInk(
    val colour: Int = PenPalette.COLORS.first(),
    val width: Float = PenPalette.DEFAULT_WIDTHS[1],
    val custom: Int? = null,
) {

    /** The same ink in [colour], promoted to opaque like every palette entry. */
    fun withColour(colour: Int): DesktopInk = copy(colour = PenPalette.opaque(colour))

    /** The same ink at [width], clamped to the range a document may carry. */
    fun withWidth(width: Float): DesktopInk = copy(width = PenPalette.clampWidth(width))

    /** The same ink with the custom slot redefined; null empties it again (never "black"). */
    fun withCustom(colour: Int?): DesktopInk = copy(custom = colour?.let(PenPalette::opaque))
}

/** What the colour panel will do with the colour it commits. */
enum class ColourEditorTarget {
    /** Write the palette's editable custom slot, and pick it. */
    CUSTOM,

    /** Append a new entry to the palette (opaque, de-duplicated, capped), and pick it. */
    NEW,
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
