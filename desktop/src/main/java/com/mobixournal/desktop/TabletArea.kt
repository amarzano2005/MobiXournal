package com.mobixournal.desktop

/**
 * How much of the tablet's surface the window maps.
 *
 * The tablet's whole surface (`FULL`) is the driver's own system context, which is what this window has
 * always read and what every other program on the machine sees. `TABLET` maps a **centred rectangle** of it
 * instead — the part a hand actually writes in — so a small hand movement covers the page without the pen
 * ever leaving the glass.
 */
enum class TabletArea(val label: String) {
    FULL("Full"),
    TABLET("Tablet"),
    ;

    companion object {
        /** [raw] as the stored key writes it, with the whole surface as the answer for anything else. */
        fun of(raw: String?): TabletArea = entries.firstOrNull { it.name == raw } ?: FULL
    }
}

/**
 * A rectangle of the tablet's surface, as **fractions of it**: 0 is the surface's left/top edge, 1 its
 * right/bottom one.
 *
 * Fractions rather than millimetres because that is all the driver declares: Wintab reports its axes in
 * **counts** (0…50800 here) and no physical size this shell can trust, so a millimetre rectangle would be a
 * number invented on the way in. A fraction is exactly what the driver can honour, and it is what the
 * context's `lcIn*` fields take.
 */
data class TabletWindowArea(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {

    val width: Double get() = x1 - x0
    val height: Double get() = y1 - y0

    /** One line for the diagnostics log: where on the surface the window listens. */
    fun describe(): String =
        "area ${percent(x0)}..${percent(x1)} x ${percent(y0)}..${percent(y1)} of the surface"

    private fun percent(value: Double): String = "${(value * 100).toInt()}%"
}

/**
 * The arithmetic of the active area, pure and testable without a tablet.
 *
 * The percentages are of the **surface**, so the same percentage on both axes keeps the tablet's own
 * ratio — which is what the aspect lock means: the height follows the width slider, and the rectangle a
 * hand draws in is the same shape as the tablet instead of a squashed one.
 */
object TabletAreaMap {

    /** The smallest area the rows offer: below this the page would move faster than the pen. */
    const val MIN_PERCENT = 10

    /** The largest: the whole surface, i.e. the same reading as [TabletArea.FULL]. */
    const val MAX_PERCENT = 100

    /** Where the Tablet-area rows start: most of the surface, with a margin a wrist does not need. */
    const val DEFAULT_PERCENT = 80

    /** The height the area takes, which the aspect lock ties to the width. */
    fun heightPercent(widthPercent: Int, heightPercent: Int, aspectLock: Boolean): Int =
        if (aspectLock) widthPercent else heightPercent

    /**
     * The rectangle to map, or null for the whole surface.
     *
     * Null is also the answer for a Tablet area that has grown to the whole surface ([MAX_PERCENT] on both
     * axes): the two readings are the same rectangle, and the system context is the cheaper one.
     */
    fun window(
        area: TabletArea,
        widthPercent: Int,
        heightPercent: Int,
        aspectLock: Boolean,
    ): TabletWindowArea? {
        if (area == TabletArea.FULL) return null
        val width = widthPercent.coerceIn(MIN_PERCENT, MAX_PERCENT) / 100.0
        val height = heightPercent(widthPercent, heightPercent, aspectLock).coerceIn(MIN_PERCENT, MAX_PERCENT) / 100.0
        if (width >= 1.0 && height >= 1.0) return null
        val x0 = (1.0 - width) / 2.0
        val y0 = (1.0 - height) / 2.0
        return TabletWindowArea(x0, y0, x0 + width, y0 + height)
    }

    /**
     * The `lcInOrg`/`lcInExt` pair for one axis: the driver's declared `min`…`max` counts, narrowed to
     * [area]'s own span of it.
     *
     * A driver that declares no range (`max <= min`) has nothing to narrow, so the answer is null and the
     * reader keeps its system context — an area the driver cannot map must not become a dead pen.
     */
    fun deviceSpan(min: Int, max: Int, from: Double, to: Double): Pair<Int, Int>? {
        if (max <= min) return null
        val counts = max - min
        val start = min + (counts * from).toInt()
        val end = min + (counts * to).toInt()
        val extent = end - start
        return if (extent <= 0) null else start to extent
    }
}
