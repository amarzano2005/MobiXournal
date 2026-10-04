package com.mobixournal.format

/**
 * Converts between `.xopp` colour strings and ARGB ints (`0xAARRGGBB`, Android's `Color`
 * layout). On disk the form is `#RRGGBBAA` (alpha **last**); we also read 6-digit `#RRGGBB`
 * (implicit opaque) and the desktop named-colour keywords. See `docs/architecture.md`.
 */
object XoppColor {

    /**
     * Named colour keywords recognised by desktop Xournal++ — parsed verbatim from the XML.
     * Maps lowercase names to ARGB int values, hex for hex with upstream's `PREDEFINED_COLORS`
     * (`src/core/control/xojfile/XmlParserHelper.cpp`), which resolves them to `Colors::` constants
     * (`src/util/include/util/Color.h`): `blue`→`xopp_royalblue`, `lightblue`→`xopp_deepskyblue`,
     * `lightgreen`→`lime`, `orange`→`xopp_darkorange`.
     */
    private val NAMED: Map<String, Int> = mapOf(
        "black" to 0xFF000000.toInt(),      // Colors::black
        "blue" to 0xFF3333CC.toInt(),       // Colors::xopp_royalblue
        "red" to 0xFFFF0000.toInt(),        // Colors::red
        "green" to 0xFF008000.toInt(),      // Colors::green
        "gray" to 0xFF808080.toInt(),       // Colors::gray
        "grey" to 0xFF808080.toInt(),       // desktop has no `grey`; kept as an alias for `gray`
        "lightblue" to 0xFF00C0FF.toInt(),  // Colors::xopp_deepskyblue
        "lightgreen" to 0xFF00FF00.toInt(), // Colors::lime
        "magenta" to 0xFFFF00FF.toInt(),    // Colors::magenta
        "orange" to 0xFFFF8000.toInt(),     // Colors::xopp_darkorange
        "yellow" to 0xFFFFFF00.toInt(),     // Colors::yellow
        "white" to 0xFFFFFFFF.toInt(),      // Colors::white
    )

    /**
     * Parse an on-disk colour string to an ARGB int. Recognises `#RRGGBBAA` (8-digit hex with alpha
     * last), `#RRGGBB` (6-digit hex, assumed opaque), and named colours (case-insensitive). Returns
     * opaque black (`0xFF000000`) for null, malformed, or unrecognised input.
     *
     * @param value The colour string from the XML (`#RRGGBBAA`, `#RRGGBB`, or a named colour).
     * @return The parsed ARGB int in `0xAARRGGBB` format, or opaque black on parse failure.
     */
    fun parse(value: String?): Int {
        if (value == null) return 0xFF000000.toInt()
        val v = value.trim()
        NAMED[v.lowercase()]?.let { return it }
        val hex = v.removePrefix("#")
        if (hex.startsWith("-") || hex.startsWith("+")) return 0xFF000000.toInt()
        return when (hex.length) {
            6 -> {
                val rgb = hex.toLongOrNull(16) ?: return 0xFF000000.toInt()
                0xFF000000.toInt() or rgb.toInt()
            }
            8 -> {
                val rgba = hex.toLongOrNull(16) ?: return 0xFF000000.toInt()
                val a = (rgba and 0xFF).toInt()
                val rgb = (rgba ushr 8).toInt() and 0xFFFFFF
                (a shl 24) or rgb
            }
            else -> 0xFF000000.toInt()
        }
    }

    /**
     * The alpha desktop Xournal++ writes into a highlighter stroke's stored colour — `#…7f`.
     *
     * Upstream's `SaveHandler::visitStroke` (src/core/control/xojfile/SaveHandler.cpp) forces
     * `alpha = 0x7f` on a highlighter stroke's colour attribute, whatever the tool colour's own
     * alpha was, so a `.xopp` saved here carries the same byte the desktop app would write. (The
     * desktop ignores this stored alpha when it paints — it renders the highlighter at a fixed
     * [`HIGHLIGHTER_RENDER_ALPHA`][com.mobixournal.render.StrokePainter] opacity in multiply — but the
     * value still has to match for the files to compare equal.)
     */
    const val HIGHLIGHTER_ALPHA = 0x7f

    /**
     * Replace the alpha channel of an ARGB int, keeping its RGB unchanged.
     *
     * @param alpha The new alpha value (0..255) to apply.
     * @return A new ARGB int with the specified alpha and the original RGB.
     */
    fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    /** Extract the alpha channel (0..255) from an ARGB int. */
    val Int.alpha: Int get() = (this ushr 24) and 0xFF

    /** Extract the red channel (0..255) from an ARGB int. */
    val Int.red: Int get() = (this ushr 16) and 0xFF

    /** Extract the green channel (0..255) from an ARGB int. */
    val Int.green: Int get() = (this ushr 8) and 0xFF

    /** Extract the blue channel (0..255) from an ARGB int. */
    val Int.blue: Int get() = this and 0xFF

    /**
     * Serialise an ARGB int to the on-disk `#RRGGBBAA` form (red, green, blue, then alpha), lowercase
     * hex with leading `#`. This is the inverse of [parse] for 8-digit hex strings.
     *
     * @param argb The colour in Android's `0xAARRGGBB` format.
     * @return The on-disk hex string in `#RRGGBBAA` format.
     */
    fun format(argb: Int): String {
        val a = (argb ushr 24) and 0xFF
        val rgb = argb and 0xFFFFFF
        val rgba = (rgb.toLong() shl 8) or a.toLong()
        return "#" + rgba.toString(16).padStart(8, '0')
    }
}
