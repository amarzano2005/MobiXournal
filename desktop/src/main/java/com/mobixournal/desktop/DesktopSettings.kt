package com.mobixournal.desktop

import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.LineStyle
import com.mobixournal.design.ShortcutKeys
import com.mobixournal.render.PressureCurve
import java.io.File
import java.util.Properties

/**
 * The desktop window's small settings store: a Java properties file beside the app's data, not a
 * database and not the Android app's settings file.
 *
 * It exists because a choice has to survive a restart, and a desktop app has somewhere ordinary to put
 * that: `%APPDATA%\MobiXournal\desktop.properties` on Windows ([defaultFile]). The file is read once and
 * rewritten on every change, so a settings write is never lost to a crash — and a read that fails (a
 * hand-edited file, a directory without permission) falls back to the documented defaults instead of
 * stopping the window from opening.
 *
 * Every property here is one row of the Settings surface (`SettingsSurface`), and every row is a hook as
 * well as a key: a choice that changed nothing on screen would be a setting the window forgot.
 */
class DesktopSettings(val file: File = defaultFile()) {

    private val properties = Properties()

    init {
        runCatching { if (file.isFile) file.inputStream().use(properties::load) }
    }

    /**
     * Where the tool strips stand — the rail down an edge, or the ribbon under the header.
     *
     * The key is the one the choice has always lived under, and [StripPlacement.of] still reads the names
     * it used to hold (`Side`, `Rail`, `Ribbon`, `Both`) **together with the old separate rail side**, so
     * an install that had already moved its rail to the right does not find it back on the left after this
     * row merged the two answers into one.
     */
    var stripPlacement: StripPlacement
        get() = StripPlacement.of(
            properties.getProperty(KEY_TOOL_PLACEMENT),
            properties.getProperty(KEY_RAIL_SIDE),
        )
        set(value) {
            properties.setProperty(KEY_TOOL_PLACEMENT, value.name)
            // The side is part of the placement now: leaving the old key behind would let a stale edge
            // answer a question this row no longer asks.
            properties.remove(KEY_RAIL_SIDE)
            save()
        }

    /**
     * The slots of the strips, in the order the reader put them — a list of [DesktopTool] names.
     *
     * Empty means **the factory order** ([DESKTOP_TOOL_GROUPS]), which is also what fills in a slot a
     * later build adds: a stored order names the tools it knows and the rest follow in their own place, so
     * a new tool cannot be hidden by an order written before it existed.
     */
    var toolOrder: List<String>
        get() = properties.getProperty(KEY_TOOL_ORDER)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(value) {
            val kept = value.map { it.trim() }.filter { it.isNotEmpty() }
            if (kept.isEmpty()) {
                properties.remove(KEY_TOOL_ORDER)
            } else {
                properties.setProperty(KEY_TOOL_ORDER, kept.joinToString(","))
            }
            save()
        }

    /**
     * Whether the pen's pressure is honoured at all (`tablet.pressureEnabled`).
     *
     * Off draws every sample at the tool's set width — the tablet's *Pressure sensitivity* switch, and the
     * way a pen stroke and a figure are made to match exactly.
     */
    var pressureEnabled: Boolean
        get() = properties.getProperty(KEY_PRESSURE_ENABLED)?.trim()?.toBooleanStrictOrNull() ?: true
        set(value) {
            properties.setProperty(KEY_PRESSURE_ENABLED, value.toString())
            save()
        }

    /**
     * The floor under a filtered pressure (`tablet.minimumPressure`), clamped to the range the row offers.
     *
     * It is desktop Xournal++'s own `minimumPressure`, so a document written here keeps the widths the
     * desktop app would have stored.
     */
    var minimumPressure: Float
        get() = properties.getProperty(KEY_MINIMUM_PRESSURE)?.toFloatOrNull()
            ?.takeIf { it.isFinite() }
            ?.coerceIn(PressureCurve.MINIMUM_PRESSURE_MIN, PressureCurve.MINIMUM_PRESSURE_MAX)
            ?: PressureCurve.MINIMUM_PRESSURE_DEFAULT
        set(value) {
            properties.setProperty(
                KEY_MINIMUM_PRESSURE,
                value.coerceIn(PressureCurve.MINIMUM_PRESSURE_MIN, PressureCurve.MINIMUM_PRESSURE_MAX)
                    .toString(),
            )
            save()
        }

    /** The `pressureMultiplier` of the same filter, clamped to the desktop's own slider range. */
    var pressureMultiplier: Float
        get() = properties.getProperty(KEY_PRESSURE_MULTIPLIER)?.toFloatOrNull()
            ?.takeIf { it.isFinite() }
            ?.coerceIn(PressureCurve.MULTIPLIER_MIN, PressureCurve.MULTIPLIER_MAX)
            ?: 1f
        set(value) {
            properties.setProperty(
                KEY_PRESSURE_MULTIPLIER,
                value.coerceIn(PressureCurve.MULTIPLIER_MIN, PressureCurve.MULTIPLIER_MAX).toString(),
            )
            save()
        }

    /**
     * The pen's saved pressure presets (`tablet.penPresets`) — the tablet's named pairs, in menu order.
     *
     * One line of `id;name;minimum;multiplier` entries, the same plain-text style as the strip's order. A
     * stored line this build cannot read falls back to the factory preset rather than emptying the menu, and
     * the list can never be stored empty: the last preset is the one the reader is drawing with.
     */
    var penPresets: List<DesktopPenPreset>
        get() = decodePenPresets(properties.getProperty(KEY_PEN_PRESETS))
        set(value) {
            val kept = value.ifEmpty { DesktopPenPreset.DEFAULT_PRESETS }
            properties.setProperty(KEY_PEN_PRESETS, encodePenPresets(kept))
            save()
        }

    /**
     * Which preset is live (`tablet.selectedPenPreset`), or the first saved one when the stored id is gone.
     *
     * Falling back rather than reporting an id nothing matches is what keeps the menu from having no chip
     * highlighted on a file that was hand-edited or written by a build with a preset this one lacks.
     */
    var selectedPenPresetId: String
        get() {
            val presets = penPresets
            val stored = properties.getProperty(KEY_SELECTED_PEN_PRESET)?.trim()
            return presets.firstOrNull { it.id == stored }?.id ?: presets.first().id
        }
        set(value) {
            properties.setProperty(KEY_SELECTED_PEN_PRESET, value)
            save()
        }

    /**
     * The width a **figure** is drawn at, in points (`figure.width`) — its own answer, not the pen's.
     *
     * A figure has no pressure, so it is always drawn at its full width; giving it a width of its own is
     * what lets a rectangle match a light pen stroke instead of the pen's widest slot.
     */
    var figureWidth: Float
        get() = properties.getProperty(KEY_FIGURE_WIDTH)?.toFloatOrNull()
            ?.let(PenPalette::clampWidth)
            ?: PenPalette.DEFAULT_WIDTHS[1]
        set(value) {
            properties.setProperty(KEY_FIGURE_WIDTH, PenPalette.clampWidth(value).toString())
            save()
        }

    /** What a new tab is written as — the Documents section's one answer. */
    var newDocumentFormat: DesktopNewDocumentFormat
        get() = DesktopNewDocumentFormat.of(properties.getProperty(KEY_NEW_DOCUMENT_FORMAT))
        set(value) {
            properties.setProperty(KEY_NEW_DOCUMENT_FORMAT, value.name)
            save()
        }

    /** The zoom a document opens at, before the reader touches anything. */
    var fitOnOpen: FitOnOpen
        get() = FitOnOpen.of(properties.getProperty(KEY_FIT_ON_OPEN))
        set(value) {
            properties.setProperty(KEY_FIT_ON_OPEN, value.name)
            save()
        }

    /** When the page list shows itself: the window's own more-than-one-page rule, always or never. */
    var sidebarPolicy: SidebarPolicy
        get() = SidebarPolicy.of(properties.getProperty(KEY_SIDEBAR_POLICY))
        set(value) {
            properties.setProperty(KEY_SIDEBAR_POLICY, value.name)
            save()
        }

    /** Whether the inspector lane may open while something is being edited. */
    var inspectorPolicy: InspectorPolicy
        get() = InspectorPolicy.of(properties.getProperty(KEY_INSPECTOR_POLICY))
        set(value) {
            properties.setProperty(KEY_INSPECTOR_POLICY, value.name)
            save()
        }

    /**
     * Whether the status bar reports the frame time the window actually paints at.
     *
     * A readout, not a behaviour: it turns one label on and nothing else, so it is safe to leave off
     * on a machine whose number nobody is watching.
     */
    var frameTime: Boolean
        get() = properties.getProperty(KEY_FRAME_TIME)?.trim()?.equals("true", ignoreCase = true) ?: true
        set(value) {
            properties.setProperty(KEY_FRAME_TIME, value.toString())
            save()
        }

    /**
     * The rate the window asks the display for **while it is full screen** (`display.targetHz`).
     *
     * A wish, not a setting the window can apply to itself: the panel's mode is the display's, and a machine
     * with no 144 Hz mode keeps its own rate while the status bar keeps reporting it. Clamped to the rates
     * the Display section offers, so a hand-edited file cannot ask for 0 Hz.
     */
    var targetHz: Int
        get() = properties.getProperty(KEY_TARGET_HZ)?.trim()?.toIntOrNull()
            ?.takeIf { it in TARGET_HZ_RANGE }
            ?: DEFAULT_TARGET_HZ
        set(value) {
            properties.setProperty(KEY_TARGET_HZ, value.coerceIn(TARGET_HZ_RANGE).toString())
            save()
        }


    /**
     * The key table this window remapped (`interface.shortcuts`), as [ShortcutKeys] writes it.
     *
     * Only the rows that **differ** from the factory ones are stored: an empty map is the default table, so
     * a new build's new commands arrive with their own keys instead of with nothing.
     */
    var shortcuts: Map<String, String>
        get() = ShortcutKeys.decode(properties.getProperty(KEY_SHORTCUTS), known = ShortcutTable.ids())
        set(value) {
            val factory = ShortcutTable.defaults()
            val changed = value.filter { (id, key) -> id in factory && factory[id] != key }
            if (changed.isEmpty()) {
                properties.remove(KEY_SHORTCUTS)
            } else {
                properties.setProperty(KEY_SHORTCUTS, ShortcutKeys.encode(changed))
            }
            save()
        }

    /**
     * How much of the tablet's surface the window maps (`tablet.activeArea`).
     *
     * `FULL` is the driver's system context, which is what the window has always read; `TABLET` opens a
     * context of its own over the centred rectangle [windowArea] describes.
     */
    var activeArea: TabletArea
        get() = TabletArea.of(properties.getProperty(KEY_ACTIVE_AREA))
        set(value) {
            properties.setProperty(KEY_ACTIVE_AREA, value.name)
            save()
        }

    /** The area's width, as a percentage of the surface (`tablet.areaWidthPct`), clamped to the rows' range. */
    var areaWidthPercent: Int
        get() = percentOf(properties.getProperty(KEY_AREA_WIDTH_PCT))
        set(value) {
            properties.setProperty(KEY_AREA_WIDTH_PCT, value.coerceIn(AREA_PERCENT_RANGE).toString())
            save()
        }

    /** The area's height, as a percentage of the surface — followed by the width while the lock is on. */
    var areaHeightPercent: Int
        get() = percentOf(properties.getProperty(KEY_AREA_HEIGHT_PCT))
        set(value) {
            properties.setProperty(KEY_AREA_HEIGHT_PCT, value.coerceIn(AREA_PERCENT_RANGE).toString())
            save()
        }

    /**
     * Whether the area keeps the tablet's own ratio (`tablet.aspectLock`, on by default).
     *
     * Both percentages are of the surface, so **the same number on both axes is the tablet's shape** — the
     * lock is what makes the height slider follow the width one.
     */
    var aspectLock: Boolean
        get() = properties.getProperty(KEY_ASPECT_LOCK)?.trim()?.toBooleanStrictOrNull() ?: true
        set(value) {
            properties.setProperty(KEY_ASPECT_LOCK, value.toString())
            save()
        }

    /** The rectangle of the surface the window maps, or null for the whole of it ([TabletAreaMap.window]). */
    fun windowArea(): TabletWindowArea? =
        TabletAreaMap.window(activeArea, areaWidthPercent, areaHeightPercent, aspectLock)

    /** A stored percentage, or the rows' own starting point when the entry is absent or unreadable. */
    private fun percentOf(raw: String?): Int =
        raw?.trim()?.toIntOrNull()?.coerceIn(AREA_PERCENT_RANGE) ?: TabletAreaMap.DEFAULT_PERCENT

    /** Which pen stream the window prefers — Wintab and Windows Ink, or whichever this host has. */
    var penStream: PenStream
        get() = PenStream.of(properties.getProperty(KEY_PEN_STREAM))
        set(value) {
            properties.setProperty(KEY_PEN_STREAM, value.name)
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

    /**
     * Every key and value this store holds, as the settings backup writes them.
     *
     * A copy, never the live table: a backup is taken while the window runs, and handing out the
     * `Properties` object would let a later write change the file being exported.
     */
    fun snapshot(): Map<String, String> = properties.stringPropertyNames()
        .sorted()
        .associateWith { properties.getProperty(it) }

    /**
     * Replace everything this store holds with [values] — an imported backup.
     *
     * Wholesale, not merged: a backup is a configuration, and a key the file does not carry is a key that
     * was *not* set when it was written, so merging would keep a value from the machine being replaced.
     * The window reads the new values through [DesktopSettingsState.reload].
     */
    fun restore(values: Map<String, String>) {
        properties.clear()
        for ((key, value) in values) properties.setProperty(key, value)
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

        /** Which edge the rail takes. */
        const val KEY_RAIL_SIDE = "interface.railSide"

        /** The zoom a document opens at. */
        const val KEY_FIT_ON_OPEN = "interface.fitOnOpen"

        /** When the page list shows itself. */
        const val KEY_SIDEBAR_POLICY = "interface.sidebarPolicy"

        /** Whether the inspector lane may open. */
        const val KEY_INSPECTOR_POLICY = "interface.inspectorPolicy"

        /** Whether the status bar reports the paint time per frame. */
        const val KEY_FRAME_TIME = "display.frameTime"

        /** The preferred pen stream. */
        const val KEY_PEN_STREAM = "tablet.penStream"

        /** The refresh rate the window asks for in full screen — a wish, honoured where the panel can. */
        const val KEY_TARGET_HZ = "display.targetHz"

        /** The exponent the pen's reading is shaped by, 1.0 = the model's own arithmetic untouched. */

        /** Which part of the tablet's surface is mapped: the whole of it, or the hand's own rectangle. */
        const val KEY_ACTIVE_AREA = "tablet.activeArea"

        /** The area's two spans, as percentages of the surface. */
        const val KEY_AREA_WIDTH_PCT = "tablet.areaWidthPct"
        const val KEY_AREA_HEIGHT_PCT = "tablet.areaHeightPct"

        /** Whether the area keeps the tablet's own ratio. */
        const val KEY_ASPECT_LOCK = "tablet.aspectLock"

        /** Whether the pen's pressure is honoured, and the two numbers of Xournal++'s filter. */
        const val KEY_PRESSURE_ENABLED = "tablet.pressureEnabled"
        const val KEY_MINIMUM_PRESSURE = "tablet.minimumPressure"
        const val KEY_PRESSURE_MULTIPLIER = "tablet.pressureMultiplier"

        /** The pen's named pressure presets, and which of them is live. */
        const val KEY_PEN_PRESETS = "tablet.penPresets"
        const val KEY_SELECTED_PEN_PRESET = "tablet.selectedPenPreset"

        /** The width a figure is drawn at, in points. */
        const val KEY_FIGURE_WIDTH = "figure.width"

        /** What a new tab is written as: `.mobix`, a gzip `.xopp` or a zipped `.xopp`. */
        const val KEY_NEW_DOCUMENT_FORMAT = "document.newFormat"

        /** The strip's slot order, as [DesktopTool] names; absent means the factory order. */
        const val KEY_TOOL_ORDER = "interface.toolOrder"

        /** The key table's remapped rows, as `id:key` pairs — only the rows that differ are written. */
        const val KEY_SHORTCUTS = "interface.shortcuts"

        /** The percentages [TabletAreaMap] accepts — the ranges the two sliders offer. */
        val AREA_PERCENT_RANGE = TabletAreaMap.MIN_PERCENT..TabletAreaMap.MAX_PERCENT

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
         *
         * [TEST_SETTINGS_PROPERTY] names another file, and the suite sets it: a test that opens a window
         * with no settings of its own must read a file that test run owns, never the one the installed
         * window on this machine has been taught (a moved rail or a hidden inspector changes what a
         * pixel test renders).
         */
        fun defaultFile(): File {
            System.getProperty(TEST_SETTINGS_PROPERTY)?.takeIf { it.isNotBlank() }?.let { return File(it) }
            val appData = System.getenv("APPDATA")
            val base = if (!appData.isNullOrBlank()) File(appData) else File(System.getProperty("user.home"))
            return File(File(base, "MobiXournal"), "desktop.properties")
        }

        /** The system property that moves the store, so a suite never reads the installed window's file. */
        const val TEST_SETTINGS_PROPERTY = "mobixournal.desktop.settings"
    }
}
