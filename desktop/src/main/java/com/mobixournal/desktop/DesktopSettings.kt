package com.mobixournal.desktop

import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.LineStyle
import java.io.File
import java.util.Properties

/**
 * Where the drawing tools stand, per `docs/ui-modernization.md` §2.
 *
 * Both placements ship: the vertical rail is the default and the horizontal ribbon is the alternative
 * for a 16:9 monitor, and `Both` shows the rail’s lane and the ribbon at once. Whichever is chosen it is
 * a band in its own lane beside the canvas — never a bar lying over the page.
 */
enum class ToolPlacement(val label: String) {
    RAIL("Rail"),
    RIBBON("Ribbon"),
    BOTH("Both"),
    ;

    /** The vertical rail's lane is drawn (Rail and Both). */
    val showsRail: Boolean get() = this != RIBBON

    /** The horizontal ribbon under the header is drawn (Ribbon and Both). */
    val showsRibbon: Boolean get() = this != RAIL

    companion object {
        /** [name] as `ToolPlacement`, falling back to [RAIL] for anything this build does not know. */
        fun of(name: String?): ToolPlacement =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: RAIL
    }
}

/**
 * The desktop window's small settings store: a Java properties file beside the app's data, not a
 * database and not the Android app's settings file.
 *
 * It exists because the placement has to survive a restart, and a desktop app has somewhere ordinary to
 * put that: `%APPDATA%\MobiXournal\desktop.properties` on Windows ([defaultFile]). The file is read
 * once and rewritten on every change, so a settings write is never lost to a crash — and a read that
 * fails (a hand-edited file, a directory without permission) falls back to the documented defaults
 * instead of stopping the window from opening.
 *
 * Later desktop settings (Interface, Display, Tablet, Dial — `desktop-settings-and-status-ui`) join this
 * same file; that is why it is a store rather than one property.
 */
class DesktopSettings(val file: File = defaultFile()) {

    private val properties = Properties()

    init {
        runCatching { if (file.isFile) file.inputStream().use(properties::load) }
    }

    /** Which tool strips the window shows. Rail is the documented default. */
    var toolPlacement: ToolPlacement
        get() = ToolPlacement.of(properties.getProperty(KEY_TOOL_PLACEMENT))
        set(value) {
            properties.setProperty(KEY_TOOL_PLACEMENT, value.name)
            save()
        }

    /**
     * The live palette the strips' colour flyout draws: opaque, de-duplicated and never empty, because
     * the decoder runs it through [PenPalette.sanitised]. A fresh or hand-edited file opens on the
     * factory eight rather than on nothing.
     */
    var paletteColours: List<Int>
        get() = PaletteCodec.decode(properties.getProperty(KEY_PALETTE_COLORS))
        set(value) {
            properties.setProperty(KEY_PALETTE_COLORS, PaletteCodec.encode(PenPalette.sanitised(value)))
            save()
        }

    /** The colour the strips show; the factory palette's first swatch until one is picked. */
    var inkColour: Int
        get() = PaletteCodec.parseHex(properties.getProperty(KEY_INK_COLOUR))
            ?.let(PenPalette::opaque)
            ?: DesktopInk().colour
        set(value) {
            properties.setProperty(KEY_INK_COLOUR, PaletteCodec.hex(value))
            save()
        }

    /**
     * The three favourite colours keys `1`–`3` pick, and `Alt`+digit redefines. Always exactly
     * [PaletteCodec.FAVOURITES] long: a short or hand-edited entry is filled from the factory palette.
     */
    var colourFavourites: List<Int>
        get() = PaletteCodec.decodeFavourites(properties.getProperty(KEY_COLOUR_FAVOURITES))
        set(value) {
            properties.setProperty(
                KEY_COLOUR_FAVOURITES,
                PaletteCodec.encode(value.map(PenPalette::opaque).take(PaletteCodec.FAVOURITES)),
            )
            save()
        }

    /**
     * The palette's editable custom slot: null while it is empty, and an absent key on disk so that
     * "empty" and "black" stay distinguishable ([PenPalette]'s rule, kept here too).
     */
    var paletteCustom: Int?
        get() = PaletteCodec.parseHex(properties.getProperty(KEY_PALETTE_CUSTOM))?.let(PenPalette::opaque)
        set(value) {
            if (value == null) {
                properties.remove(KEY_PALETTE_CUSTOM)
            } else {
                properties.setProperty(KEY_PALETTE_CUSTOM, PaletteCodec.hex(value))
            }
            save()
        }

    /**
     * The highlighter's own colour — the tablet keeps the two tools apart the same way, so switching to
     * the highlighter does not repaint the pen.
     */
    var highlighterColour: Int
        get() = PaletteCodec.parseHex(properties.getProperty(KEY_HIGHLIGHTER_COLOUR))
            ?.let(PenPalette::opaque)
            ?: DesktopInks().highlighter.colour
        set(value) {
            properties.setProperty(KEY_HIGHLIGHTER_COLOUR, PaletteCodec.hex(value))
            save()
        }

    /** The highlighter's stroke width, in points, clamped to [PenPalette]'s range. */
    var highlighterWidth: Float
        get() = properties.getProperty(KEY_HIGHLIGHTER_WIDTH)?.toFloatOrNull()
            ?.let(PenPalette::clampWidth)
            ?: DesktopInks().highlighter.width
        set(value) {
            properties.setProperty(KEY_HIGHLIGHTER_WIDTH, PenPalette.clampWidth(value).toString())
            save()
        }

    /** The highlighter's three favourites: its own, exactly as the tablet keeps a terna per tool. */
    var highlighterFavourites: List<Int>
        get() = PaletteCodec.decodeFavourites(properties.getProperty(KEY_HIGHLIGHTER_FAVOURITES))
        set(value) {
            properties.setProperty(
                KEY_HIGHLIGHTER_FAVOURITES,
                PaletteCodec.encode(value.map(PenPalette::opaque).take(PaletteCodec.FAVOURITES)),
            )
            save()
        }

    /** The stroke width the strips show, in points, clamped to [PenPalette]'s range. */
    var inkWidth: Float
        get() = properties.getProperty(KEY_INK_WIDTH)?.toFloatOrNull()
            ?.let(PenPalette::clampWidth)
            ?: DesktopInk().width
        set(value) {
            properties.setProperty(KEY_INK_WIDTH, PenPalette.clampWidth(value).toString())
            save()
        }

    /** The pen's stroke pattern — the tablet's solid/dashed/dash-dot/dotted choice. */
    var inkLineStyle: LineStyle
        get() = lineStyleOf(properties.getProperty(KEY_INK_LINE_STYLE))
        set(value) {
            properties.setProperty(KEY_INK_LINE_STYLE, value.name)
            save()
        }

    /** The highlighter's own stroke pattern, kept apart exactly as its colour and width are. */
    var highlighterLineStyle: LineStyle
        get() = lineStyleOf(properties.getProperty(KEY_HIGHLIGHTER_LINE_STYLE))
        set(value) {
            properties.setProperty(KEY_HIGHLIGHTER_LINE_STYLE, value.name)
            save()
        }

    /**
     * The size a **new sheet** is born with, as the format section's **Set as default** stores it.
     *
     * It is the window's half of the tablet's *Set as default*: a reader who writes on A5 has no business
     * resizing every new page by hand. Read back as A4 when the key is absent (the first run) or when the
     * stored pair is not a page at all, so a hand-edited file cannot open a sheet of no size.
     */
    var pageWidthPt: Double
        get() = pagePt(KEY_PAGE_WIDTH, DesktopDocuments.PAGE_WIDTH_PT)
        set(value) = writePageSize(value, pageHeightPt)

    var pageHeightPt: Double
        get() = pagePt(KEY_PAGE_HEIGHT, DesktopDocuments.PAGE_HEIGHT_PT)
        set(value) = writePageSize(pageWidthPt, value)

    /** Store a default sheet size in one write, so the two halves can never disagree. */
    fun setDefaultPageSize(widthPt: Double, heightPt: Double) = writePageSize(widthPt, heightPt)

    private fun writePageSize(widthPt: Double, heightPt: Double) {
        if (!(widthPt > 0.0) || !(heightPt > 0.0)) return
        properties.setProperty(KEY_PAGE_WIDTH, widthPt.toString())
        properties.setProperty(KEY_PAGE_HEIGHT, heightPt.toString())
        save()
    }

    private fun pagePt(key: String, fallback: Double): Double =
        properties.getProperty(key)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: fallback

    /** Write the file; a failure leaves the window running on the value just chosen. */
    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { properties.store(it, "MobiXournal desktop settings") }
        }
    }

    companion object {
        /** The property the placement is stored under, named for the settings section that owns it. */
        const val KEY_TOOL_PLACEMENT = "interface.toolPlacement"

        /** The palette the colour flyout draws, as [PaletteCodec]'s comma-separated hex list. */
        const val KEY_PALETTE_COLORS = "palette.colours"

        /** The colour the strips show, as [PaletteCodec] hex. */
        const val KEY_INK_COLOUR = "ink.colour"

        /** The three favourite colours keys `1`–`3` pick, as [PaletteCodec]'s hex list. */
        const val KEY_COLOUR_FAVOURITES = "interface.colourFavourites"

        /** The palette's editable custom slot; absent while the slot is empty. */
        const val KEY_PALETTE_CUSTOM = "palette.custom"

        /** The highlighter's colour, as [PaletteCodec] hex. */
        const val KEY_HIGHLIGHTER_COLOUR = "ink.highlighterColour"

        /** The highlighter's stroke width, in points. */
        const val KEY_HIGHLIGHTER_WIDTH = "ink.highlighterWidth"

        /** The highlighter's three favourite colours, as [PaletteCodec]'s hex list. */
        const val KEY_HIGHLIGHTER_FAVOURITES = "interface.highlighterFavourites"

        /** The stroke width the strips show, in points. */
        const val KEY_INK_WIDTH = "ink.width"

        /** The default sheet a new page is born with, in points — the format section's Set as default. */
        const val KEY_PAGE_WIDTH = "page.width"
        const val KEY_PAGE_HEIGHT = "page.height"

        /** The pen's stroke pattern, as [LineStyle]'s name. */
        const val KEY_INK_LINE_STYLE = "ink.lineStyle"

        /** The highlighter's stroke pattern, as [LineStyle]'s name. */
        const val KEY_HIGHLIGHTER_LINE_STYLE = "ink.highlighterLineStyle"

        /** [LineStyle] named by [name], or the solid default for an unknown or absent entry. */
        fun lineStyleOf(name: String?): LineStyle =
            LineStyle.entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
                ?: LineStyle.PLAIN

        /**
         * The store's home: `%APPDATA%\MobiXournal\desktop.properties` on Windows, and the same
         * directory name under the user's home where `APPDATA` is not set.
         */
        fun defaultFile(): File {
            val appData = System.getenv("APPDATA")
            val base = if (!appData.isNullOrBlank()) File(appData) else File(System.getProperty("user.home"))
            return File(File(base, "MobiXournal"), "desktop.properties")
        }
    }
}
