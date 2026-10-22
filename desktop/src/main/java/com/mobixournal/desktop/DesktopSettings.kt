package com.mobixournal.desktop

import com.mobixournal.design.PenPalette
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

    /** The stroke width the strips show, in points, clamped to [PenPalette]'s range. */
    var inkWidth: Float
        get() = properties.getProperty(KEY_INK_WIDTH)?.toFloatOrNull()
            ?.let(PenPalette::clampWidth)
            ?: DesktopInk().width
        set(value) {
            properties.setProperty(KEY_INK_WIDTH, PenPalette.clampWidth(value).toString())
            save()
        }

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

        /** The stroke width the strips show, in points. */
        const val KEY_INK_WIDTH = "ink.width"

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
