package com.mobixournal.ui

import android.content.Context
import com.mobixournal.render.BarrelAction
import com.mobixournal.render.BarrelDoubleAction
import com.mobixournal.render.GuideKind
import com.mobixournal.render.Momentum
import com.mobixournal.render.MomentumCurve
import com.mobixournal.render.PageStacker
import com.mobixournal.render.PanSensitivity
import com.mobixournal.render.PressureCurve
import com.mobixournal.render.StrokePrecision

/**
 * Which edge of the editor the tool rail is docked to.
 *
 * The rail's buttons are laid out in a row when [isHorizontal] is true (top/bottom), or in a column
 * when docked to a vertical edge (left/right).
 */
enum class ToolbarPosition(val label: String) {
    /** Rail along the top edge, buttons in a horizontal row. */
    TOP("Top"),
    /** Rail along the bottom edge, buttons in a horizontal row. */
    BOTTOM("Bottom"),
    /** Rail down the left edge, buttons in a vertical column. */
    LEFT("Left"),
    /** Rail down the right edge, buttons in a vertical column. */
    RIGHT("Right"),
    ;

    /** True when the rail runs along a horizontal edge (top/bottom) and so lays its buttons in a row. */
    val isHorizontal: Boolean get() = this == TOP || this == BOTTOM
}

/**
 * Which row of the canvas the "page X of Y" badge sits in.
 *
 * Paired with [PageCounterHorizontal] to place the badge in any of the nine positions on the canvas.
 */
enum class PageCounterVertical(val label: String) {
    /** Badge aligned to the top edge of the canvas. */
    TOP("Top"),
    /** Badge centered vertically on the canvas. */
    CENTER("Center"),
    /** Badge aligned to the bottom edge of the canvas. */
    BOTTOM("Bottom"),
}

/**
 * Which column of the canvas the "page X of Y" badge sits in.
 *
 * Paired with [PageCounterVertical] to place the badge in any of the nine positions on the canvas.
 */
enum class PageCounterHorizontal(val label: String) {
    /** Badge aligned to the left edge of the canvas. */
    LEFT("Left"),
    /** Badge centered horizontally on the canvas. */
    CENTER("Center"),
    /** Badge aligned to the right edge of the canvas. */
    RIGHT("Right"),
}

/**
 * Which Material 3 colour scheme the app's chrome uses.
 *
 * SYSTEM defers to the device's light/dark setting; LIGHT and DARK force a specific scheme. The theme
 * is applied to the whole app via [XoppTheme][com.mobixournal.ui.theme.XoppTheme].
 */
enum class ThemeMode(val label: String) {
    /** Follow the system's light/dark setting. */
    SYSTEM("System"),
    /** Always use the light colour scheme. */
    LIGHT("Light"),
    /** Always use the dark colour scheme. */
    DARK("Dark"),
}

/**
 * The app's user-adjustable preferences (the Settings screen edits these; [SettingsStore] persists
 * them). Kept small and serialisable to `SharedPreferences` — these are input-layer behaviours only
 * and never touch the `.xopp` format.
 *
 * @property fingerDraws When false, fingers only pan/zoom — never draw (palm-safe for stylus users).
 *   Off by default, so a first launch never lets a resting hand ink the page.
 * @property barrelAction What the stylus primary barrel-button does while held.
 * @property barrelDoubleAction What a rapid double-click of that button does (recognised only with the tip off the glass).
 * @property showHover Show a preview ring where a hovering stylus will land.
 * @property pressureEnabled Whether the pen's width follows pressure at all — desktop Xournal++'s "Pressure sensitivity". Off draws every stroke at the size setting, so a pen stroke and a shape match.
 * @property pressureMultiplier Scales the raw pressure before the minimum floor — desktop Xournal++'s "pressure multiplier".
 * @property minimumPressure The floor under the filtered pressure — desktop Xournal++'s "minimum pressure".
 * @property strokePrecision How much digitiser detail a freehand stroke keeps (fidelity vs. file size).
 * @property recognizeShapes Snap a finished freehand stroke to the primitive it resembles — line, triangle, rectangle or circle, classified by desktop Xournal++'s own recognizer. On by default.
 * @property pageColumns Pages shown side by side in the page overview: 1 is the plain single-page stack.
 * @property snapToGrid Pull shape-tool endpoints onto the page background's ruling (grid/lined sheets only).
 * @property snapRotation Pull a selection's rotate handle onto 15-degree increments.
 * @property guideKind Which on-canvas drawing guide (setsquare/compass) is laid on the page, restored on launch.
 * @property penWidths The three user-configurable pen-tip widths (pt) behind the S/M/L size slots, in slot order.
 * @property customColor The user-defined colour (opaque ARGB) behind the palette's editable custom slot.
 * @property defaultTool Which tool is active when a document first opens.
 * @property momentum How far a released pan keeps gliding — the momentum-strength factor (0 = off, 1 = normal).
 * @property momentumCurve The velocity→coast response shape for momentum (linear … exponential).
 * @property panSensitivity How far the document moves per unit of pan travel (0 = frozen, 1 = one-to-one, >1 = faster).
 * @property toolbarPosition Which edge of the editor the tool rail is docked to.
 * @property penColors The pen palette every colour picker draws from, in display order — seeded with
 *   [PEN_COLORS] (desktop Xournal++) and editable under **Settings → Colors**. Sanitised to a
 *   non-empty, de-duplicated list of opaque colours, so a corrupt pref degrades instead of blanking
 *   the palette.
 * @property lastColor The pen colour in use when the app last ran, restored on the next launch.
 *   The palette itself is [penColors]; the factory default is [PEN_COLORS], desktop Xournal++'s black.
 * @property lastWidth The pen width (pt) in use when the app last ran, restored on the next launch.
 * @property shapeWidth The width (pt) the line/shape tools draw at, restored on the next launch and kept separate from the pen's own width.
 * @property defaultShapeSlot Which pen-width slot the figure tools start at — the "default figure size". Figures draw at the set width with no pressure, so with pen pressure on a figure reads thicker than a light pen stroke; a smaller slot here closes that gap.
 * @property shapeOrder The Shapes submenu's members in display order, by [EditorTool.name]. Empty means the factory order.
 * @property shapeHidden The [EditorTool.name]s hidden from the Shapes submenu. Empty means all are shown.
 * @property toolGroupSelections Which tool each grouped rail slot currently stands for, keyed by [ToolGroup.id]. Missing entries fall back to the group's first tool.
 * @property railOrder The rail's button positions in display order, by [RailItem.id]. Empty means the factory order.
 * @property railHidden The [RailItem.id]s the user has hidden from the rail. Empty means everything is shown.
 * @property audioFolderUri The persisted `OpenDocumentTree` URI of the folder audio sidecars are kept in (empty = none nominated).
 * @property pageCounterVertical Which row of the canvas the always-visible page counter sits in.
 * @property pageCounterHorizontal Which column of the canvas the always-visible page counter sits in.
 * @property themeMode Light, dark, or follow the system — applied to the whole app's Material 3 scheme.
 * @property dynamicColor Whether to use Android 12+ dynamic colours from the wallpaper (Material You).
 * @property toolShortcutKeys Per-tool keyboard shortcut, keyed by [EditorTool]; empty/absent means disabled.
 * @property colorShortcutKeys Per-colour keyboard shortcut, keyed by the ARGB value of the swatch; empty/absent means disabled.
 * @property presets The user's saved tool snapshots, in display order.
 * @property textImportLimitMb Largest plain-text file (MiB) that may be typeset into a background PDF.
 * @property pdfCacheLimitMb How much (MiB) the generated/background PDF cache may keep.
 */
data class AppSettings(
    /** Default page width in points for new documents. */
    val defaultPageWidthPt: Double = DEFAULT_PAGE_WIDTH_PT,
    /** Default page height in points for new documents. */
    val defaultPageHeightPt: Double = DEFAULT_PAGE_HEIGHT_PT,
    /**
     * When false, fingers only pan/zoom — never draw (palm-safe for stylus users). Off by default
     * on a first launch, so a hand resting on the glass never inks a page written on with a stylus.
     */
    val fingerDraws: Boolean = false,
    /** What the stylus primary barrel-button does while held. */
    val barrelAction: BarrelAction = BarrelAction.ERASE,
    /** What a rapid double-click of that button does (recognised only with the tip off the glass). */
    val barrelDoubleAction: BarrelDoubleAction = BarrelDoubleAction.UNDO,
    /** Show a preview ring where a hovering stylus will land. */
    val showHover: Boolean = true,
    /**
     * Whether the pen's width follows pressure at all — desktop Xournal++'s "Pressure sensitivity".
     * Switched off, every stroke is drawn at the size setting, so a pen stroke and a shape come out
     * the same thickness; on restores the taper.
     */
    val pressureEnabled: Boolean = true,
    /**
     * Scales the raw pressure before the minimum floor — desktop Xournal++'s "pressure multiplier".
     * Raise it if you write lightly and want thicker lines; 1 leaves the pressure as the digitiser
     * reports it, and above 1 a stroke can exceed its nominal width (as on the desktop).
     */
    val pressureMultiplier: Float = 1f,
    /**
     * The floor under the filtered pressure — desktop Xournal++'s "minimum pressure". A zero-pressure
     * sample still leaves a light line at this fraction of the pen's width. Desktop's default is 0.05.
     */
    val minimumPressure: Float = PressureCurve.MINIMUM_PRESSURE_DEFAULT,
    /** How much digitiser detail a freehand stroke keeps (fidelity vs. file size). */
    val strokePrecision: StrokePrecision = StrokePrecision.DEFAULT,
    /** Snap a finished freehand stroke to the primitive it resembles (line, circle, rectangle…). */
    val recognizeShapes: Boolean = true,
    /** Pages shown side by side in the page overview: 1 is the plain single-page stack. */
    val pageColumns: Int = 1,
    /** Pull shape-tool endpoints onto the page background's ruling (grid/lined sheets only). */
    val snapToGrid: Boolean = false,
    /** Pull a selection's rotate handle onto 15-degree increments. */
    val snapRotation: Boolean = false,
    /** Which on-canvas drawing guide (setsquare/compass) is laid on the page, restored on launch. */
    val guideKind: GuideKind = GuideKind.NONE,
    /** The three user-configurable pen-tip widths (pt) behind the S/M/L size slots, in slot order. */
    val penWidths: List<Float> = DEFAULT_PEN_WIDTHS,
    /** The user-defined colour (opaque ARGB) behind the palette's editable custom slot. */
    val customColor: Int = DEFAULT_CUSTOM_COLOR,
    /** Which tool is active when a document first opens. */
    val defaultTool: EditorTool = EditorTool.PEN,
    /** Key to toggle between pen and eraser (empty = disabled). */
    val penEraserToggleKey: String = "B",
    /** Key to toggle hand/pan tool (empty = disabled). */
    val handToggleKey: String = "H",
    /** How far a released pan keeps gliding — the momentum-strength factor (0 = off, 1 = normal). */
    val momentum: Float = Momentum.NORMAL,
    /** The velocity→coast response shape for momentum (linear … exponential). */
    val momentumCurve: MomentumCurve = MomentumCurve.QUADRATIC,
    /** How far the document moves per unit of pan travel (0 = frozen, 1 = one-to-one, >1 = faster). */
    val panSensitivity: Float = PanSensitivity.NORMAL,
    /** Which edge of the editor the tool rail is docked to. */
    val toolbarPosition: ToolbarPosition = ToolbarPosition.LEFT,
    /** When true, drawing tools are displayed in the empty space of the top app bar (dual toolbar mode). */
    val showToolsInTopBar: Boolean = DEFAULT_SHOW_TOOLS_IN_TOP_BAR,
    /**
     * The pen palette every colour picker draws from, in display order. Seeded with [PEN_COLORS] and
     * edited under **Settings → Colors**; an empty or unparsable stored list falls back to the
     * factory palette rather than leaving the app with no swatches at all.
     */
    val penColors: List<Int> = PEN_COLORS,
    /** The pen colour in use when the app last ran, restored on the next launch. */
    val lastColor: Int = DEFAULT_LAST_COLOR,
    /** The pen width (pt) in use when the app last ran, restored on the next launch. */
    val lastWidth: Float = DEFAULT_PEN_WIDTHS[1],
    /** The highlighter colour (opaque ARGB), restored on launch. */
    val highlighterColor: Int = DEFAULT_HIGHLIGHTER_COLOR,
    /** The highlighter width (pt), restored on launch. */
    val highlighterWidth: Float = DEFAULT_HIGHLIGHTER_WIDTH,
    /**
     * The width (pt) the line/shape tools draw at, restored on launch. Kept apart from the pen's own
     * [lastWidth] so switching to a shape doesn't inherit a hairline pen (or drag the pen thick) —
     * the factory default is the thickest pen-width slot.
     */
    val shapeWidth: Float = DEFAULT_SHAPE_WIDTH,
    /**
     * Which of the pen's width slots ([penWidths]) the figure tools start at — the "default figure
     * size". Figures draw at the set width with no pressure, so with pen pressure **on** a figure
     * can read thicker than a light pen stroke; picking a smaller slot here is the knob that closes
     * that gap. Applied to [shapeWidth] when chosen, and restored on launch.
     */
    val defaultShapeSlot: Int = DEFAULT_SHAPE_SLOT,
    /** Default number of rows for inserted tables. */
    val tableRows: Int = DEFAULT_TABLE_ROWS,
    /** Default number of columns for inserted tables. */
    val tableCols: Int = DEFAULT_TABLE_COLS,
    /** When true, inserted/drawn tables include a double horizontal line under row 1 for relational headers. */
    val tableHeader: Boolean = DEFAULT_TABLE_HEADER,
    /**
     * The Shapes submenu's members in display order, by [EditorTool.name]. Empty means the factory
     * order; names the list omits are appended (see [orderedShapeTools]).
     */
    val shapeOrder: List<String> = emptyList(),
    /** The [EditorTool.name]s hidden from the Shapes submenu. Empty means all are shown. */
    val shapeHidden: Set<String> = emptySet(),
    /**
     * Which tool each grouped rail slot currently stands for, keyed by [ToolGroup.id]. Missing
     * entries fall back to the group's first tool (see [selected]).
     */
    val toolGroupSelections: Map<String, EditorTool> = emptyMap(),
    /**
     * The rail's button positions in display order, by [RailItem.id]. Empty means the factory order;
     * ids the list omits are appended in factory order (see [orderedRailItems]).
     */
    val railOrder: List<String> = emptyList(),
    /**
     * The [RailItem.id]s the user has hidden from the rail. The factory default hides the
     * drawing-tool groups already shown in the secondary top bar (see [DEFAULT_RAIL_HIDDEN]),
     * so dual-toolbar installs avoid redundancy out of the box.
     */
    val railHidden: Set<String> = DEFAULT_RAIL_HIDDEN,
    /** The secondary (top) bar's button positions in display order. Empty falls back to factory default. */
    val topBarOrder: List<String> = DEFAULT_TOP_BAR_ORDER,
    /** The button ids the user has hidden from the secondary top bar. */
    val topBarHidden: Set<String> = emptySet(),
    /**
     * The persisted `OpenDocumentTree` URI of the folder audio sidecars are kept in (empty = none
     * nominated). A `.xopp` references its recordings by bare file name, so they have to live in a
     * folder we can both read and write — see [com.mobixournal.audio.AudioStore].
     */
    val audioFolderUri: String = "",
    /** Which row of the canvas the always-visible page counter sits in. */
    val pageCounterVertical: PageCounterVertical = PageCounterVertical.BOTTOM,
    /** Which column of the canvas the always-visible page counter sits in. */
    val pageCounterHorizontal: PageCounterHorizontal = PageCounterHorizontal.RIGHT,
    /** Light, dark, or follow the system — applied to the whole app's Material 3 scheme. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Whether to use Android 12+ dynamic colours from the wallpaper (Material You). */
    val dynamicColor: Boolean = true,
    /**
     * Per-tool keyboard shortcut, keyed by [EditorTool]. Absent or empty means that tool has no
     * shortcut. Every tool is covered, so the Settings screen can offer a key field for each.
     */
    val toolShortcutKeys: Map<EditorTool, String> = emptyMap(),
    /**
     * Per-colour keyboard shortcut, keyed by the swatch's ARGB value (see [PEN_COLORS]). Absent or
     * empty means that colour has no shortcut. Pressing it selects the pen in that colour.
     */
    val colorShortcutKeys: Map<Int, String> = emptyMap(),
    /** The user's saved tool snapshots, in display order (see [ToolPreset]). */
    val presets: List<ToolPreset> = emptyList(),
    /**
     * Largest plain-text file (MiB) that may be typeset into a background PDF. Typesetting now reads
     * and lays out the file a line at a time, so the cap bounds *time* and output size rather than
     * memory — but an unbounded import of a several-hundred-megabyte log would still take minutes
     * and fill the cache, so past this the open is refused with a message instead.
     */
    val textImportLimitMb: Int = DEFAULT_TEXT_IMPORT_LIMIT_MB,
    /**
     * How much (MiB) the generated/background PDF cache may keep. Pruning drops unreferenced files
     * oldest-first once the folder exceeds this; a dropped generated PDF simply regenerates.
     */
    val pdfCacheLimitMb: Int = DEFAULT_PDF_CACHE_LIMIT_MB,
    /** Whether the user has seen or dismissed the first-launch onboarding popup. */
    val hasSeenOnboarding: Boolean = false,
) {
    /** [textImportLimitMb] in bytes — what [com.mobixournal.io.DocumentIo] actually enforces. */
    val textImportLimitBytes: Long get() = textImportLimitMb.toLong() * BYTES_PER_MB

    /** [pdfCacheLimitMb] in bytes — what [com.mobixournal.io.PdfStore] prunes against. */
    val pdfCacheLimitBytes: Long get() = pdfCacheLimitMb.toLong() * BYTES_PER_MB

    /**
     * This settings object with every numeric field forced back into the range the UI assumes —
     * what [SettingsStore.load] applies to whatever the pref file happens to hold.
     *
     * A stored value out of range isn't cosmetic: a stored `pageColumns` of 0 makes the pages
     * popup's mode rows unreachable.
     */
    fun sanitized(): AppSettings = copy(
        // Every entry survives, custom hexes included: pruning to the named desktop colours is what
        // used to delete a user's own swatches on the next launch, and out of their JSON backups
        // with them. Opaque ARGB stays the palette's invariant, so a colour is normalised, not judged.
        penColors = penColors.map { it or 0xFF000000.toInt() }
            .distinct()
            .take(MAX_PEN_COLORS)
            .ifEmpty { PEN_COLORS },
        penWidths = penWidths.map { it.coerceIn(PEN_WIDTH_MIN, PEN_WIDTH_MAX) },
        lastWidth = lastWidth.coerceIn(PEN_WIDTH_MIN, PEN_WIDTH_MAX),
        shapeWidth = shapeWidth.coerceIn(PEN_WIDTH_MIN, PEN_WIDTH_MAX),
        defaultShapeSlot = defaultShapeSlot.coerceIn(0, penWidths.lastIndex.coerceAtLeast(0)),
        tableRows = tableRows.coerceIn(TABLE_DIMENSION_MIN, TABLE_DIMENSION_MAX),
        tableCols = tableCols.coerceIn(TABLE_DIMENSION_MIN, TABLE_DIMENSION_MAX),
        pressureMultiplier = pressureMultiplier.coerceIn(PRESSURE_MULTIPLIER_MIN, PRESSURE_MULTIPLIER_MAX),
        minimumPressure = minimumPressure.coerceIn(PressureCurve.MINIMUM_PRESSURE_MIN, 1f),
        pageColumns = pageColumns.coerceIn(1, PageStacker.COLUMN_CHOICES.last()),
    )

    /**
     * This settings object with [color] written back as the pen colour restored on the next launch.
     *
     * There is no recently-used list any more — the palette itself is the list — so this is all a
     * colour pick has to persist beyond the canvas.
     */
    fun withColorUsed(color: Int): AppSettings = copy(lastColor = color)

    /**
     * This settings object with a width change made while [tool] is live written into **that**
     * tool's own slot: the pen keeps [lastWidth], the highlighter [highlighterWidth], the figures
     * [shapeWidth]. A tool that doesn't ink shows the pen's width, so it writes the pen's slot.
     *
     * Every width change funnels through this — the toolbar slider and the tool presets — because a
     * width that lands in the wrong slot is a pen that comes back thick: set the highlighter fat
     * here, and the next switch to the pen would restore exactly that fatness.
     */
    fun withWidthFor(tool: EditorTool, width: Float): AppSettings = when {
        tool == EditorTool.HIGHLIGHTER -> copy(highlighterWidth = width)
        tool in SHAPE_TOOLS -> copy(shapeWidth = width)
        else -> copy(lastWidth = width)
    }

    companion object {
        /** Default A4 width in points. */
        const val DEFAULT_PAGE_WIDTH_PT = 595.276
        /** Default A4 height in points. */
        const val DEFAULT_PAGE_HEIGHT_PT = 841.89

        const val KEY_DEFAULT_PAGE_WIDTH = "default_page_width_pt"
        const val KEY_DEFAULT_PAGE_HEIGHT = "default_page_height_pt"
        /** Factory defaults for the three pen-width slots — the old fixed S/M/L values. */
        val DEFAULT_PEN_WIDTHS: List<Float> = listOf(0.85f, 1.5f, 2.6f)

        /** Factory default for the custom colour slot — a violet not already in the fixed palette. */
        val DEFAULT_CUSTOM_COLOR: Int = 0xFF9C27B0.toInt()

        /**
         * Factory default pen colour — the first entry of the fixed palette ([PEN_COLORS], the
         * desktop Xournal++ palette's black), so a fresh document opens on the same colour the
         * desktop app would.
         */
        val DEFAULT_LAST_COLOR: Int = XOPP_BLACK

        /** Factory default highlighter colour — the palette's yellow, as on desktop Xournal++. */
        val DEFAULT_HIGHLIGHTER_COLOR: Int = XOPP_YELLOW

        /** Factory default highlighter width — the largest pen-width slot, the one width that stays bold. */
        val DEFAULT_HIGHLIGHTER_WIDTH: Float = DEFAULT_PEN_WIDTHS[2]

        /**
         * Factory default line/shape width — the **middle** pen-width slot.
         *
         * This was the thickest slot until the pen, too, was moved to the middle: a shape is drawn
         * at its width with no pressure, so at the top slot it opened noticeably fatter than the
         * average pen stroke and had to be walked back by hand in Settings → Figures. Starting both
         * at M is what makes a fresh shape and fresh handwriting the same weight.
         */
        val DEFAULT_SHAPE_WIDTH: Float = DEFAULT_PEN_WIDTHS[1]

        /** Factory default figure size slot — the middle, matching [DEFAULT_SHAPE_WIDTH]. */
        const val DEFAULT_SHAPE_SLOT: Int = 1

        /** Default number of rows and columns for inserted tables. */
        const val DEFAULT_TABLE_ROWS: Int = 3
        const val DEFAULT_TABLE_COLS: Int = 3
        const val DEFAULT_TABLE_HEADER: Boolean = false
        const val DEFAULT_SHOW_TOOLS_IN_TOP_BAR: Boolean = true

        /**
         * Rail items hidden by default — the drawing-tool groups the secondary top bar already
         * shows (geometric shapes, arrows, table, circuits, logic gates, guides). Hides them from
         * the primary rail to avoid redundancy when [DEFAULT_SHOW_TOOLS_IN_TOP_BAR] is on.
         * The user can re-enable any of them in **Settings → Toolbar → Rail buttons**.
         */
        val DEFAULT_RAIL_HIDDEN: Set<String> = setOf(
            "line", "rectangle", "shape", "arrow", "table", "circuit", "logic", "guides",
        )
        const val TABLE_DIMENSION_MIN: Int = 1
        const val TABLE_DIMENSION_MAX: Int = 50

        /** The pressure-multiplier slider's range — desktop Xournal++'s "pressure multiplier". */
        const val PRESSURE_MULTIPLIER_MIN: Float = 0.5f
        const val PRESSURE_MULTIPLIER_MAX: Float = 2.5f

        /**
         * The most colours the pen palette may hold. A ceiling keeps the swatch grid a grid — past
         * this the picker wraps onto a third and fourth line and stops being glanceable — while still
         * leaving room for every desktop swatch plus a working set of one's own.
         */
        const val MAX_PEN_COLORS: Int = 24

        /** One mebibyte, the unit both storage caps are expressed in. */
        const val BYTES_PER_MB: Long = 1024L * 1024L

        /**
         * Default text-import cap. Measured on the streaming typesetting path: a 64 MiB source is
         * ~16k pages in about 11 s and stays well inside a 512 MiB app heap, where the old
         * whole-file path was already near the edge at 16 MiB. Comfortably larger than any
         * hand-written note, still far under a full log dump.
         */
        const val DEFAULT_TEXT_IMPORT_LIMIT_MB: Int = 64

        /** Default background-PDF cache budget. */
        const val DEFAULT_PDF_CACHE_LIMIT_MB: Int = 256

        /** The cap values the Storage section offers, in MiB. */
        val TEXT_IMPORT_LIMIT_CHOICES: List<Int> = listOf(1, 16, 64, 128, 256)

        /** The cache-budget values the Storage section offers, in MiB. */
        val PDF_CACHE_LIMIT_CHOICES: List<Int> = listOf(64, 128, 256, 512, 1024)
    }
}

/**
 * Reads and writes [AppSettings] to a small `SharedPreferences` file named `xopp_settings`.
 *
 * Each preference key is a private constant; the store maps between the persisted form and the
 * in-memory [AppSettings] data class. Unknown enum names or out-of-range numeric values fall back
 * to defaults so a pref file written by a newer build degrades gracefully.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("xopp_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            defaultPageWidthPt = prefs.getFloat(KEY_DEFAULT_PAGE_WIDTH, d.defaultPageWidthPt.toFloat()).toDouble(),
            defaultPageHeightPt = prefs.getFloat(KEY_DEFAULT_PAGE_HEIGHT, d.defaultPageHeightPt.toFloat()).toDouble(),
            penEraserToggleKey = prefs.getString(KEY_PEN_ERASER_TOGGLE, d.penEraserToggleKey) ?: d.penEraserToggleKey,
            handToggleKey = prefs.getString(KEY_HAND_TOGGLE, d.handToggleKey) ?: d.handToggleKey,
            fingerDraws = prefs.getBoolean(KEY_FINGER_DRAWS, d.fingerDraws),
            barrelAction = enumOr(prefs.getString(KEY_BARREL, null), d.barrelAction),
            barrelDoubleAction = enumOr(prefs.getString(KEY_BARREL_DOUBLE, null), d.barrelDoubleAction),
            showHover = prefs.getBoolean(KEY_HOVER, d.showHover),
            pressureEnabled = prefs.getBoolean(KEY_PRESSURE_ENABLED, d.pressureEnabled),
            pressureMultiplier = prefs.getFloat(KEY_PRESSURE_MULTIPLIER, d.pressureMultiplier),
            minimumPressure = prefs.getFloat(KEY_MINIMUM_PRESSURE, d.minimumPressure),
            strokePrecision = enumOr(prefs.getString(KEY_STROKE_PRECISION, null), d.strokePrecision),
            recognizeShapes = prefs.getBoolean(KEY_RECOGNIZE_SHAPES, d.recognizeShapes),
            pageColumns = prefs.getInt(KEY_PAGE_COLUMNS, d.pageColumns),
            snapToGrid = prefs.getBoolean(KEY_SNAP_GRID, d.snapToGrid),
            snapRotation = prefs.getBoolean(KEY_SNAP_ROTATION, d.snapRotation),
            guideKind = enumOr(prefs.getString(KEY_GUIDE_KIND, null), d.guideKind),
            penWidths = d.penWidths.mapIndexed { i, w -> prefs.getFloat(keyPenWidth(i), w) },
            customColor = prefs.getInt(KEY_CUSTOM_COLOR, d.customColor),
            defaultTool = enumOr(prefs.getString(KEY_DEFAULT_TOOL, null), d.defaultTool),
            momentum = Momentum.coerce(prefs.getFloat(KEY_MOMENTUM, d.momentum)),
            momentumCurve = enumOr(prefs.getString(KEY_MOMENTUM_CURVE, null), d.momentumCurve),
            panSensitivity = PanSensitivity.coerce(prefs.getFloat(KEY_PAN_SENSITIVITY, d.panSensitivity)),
            toolbarPosition = enumOr(prefs.getString(KEY_TOOLBAR_POSITION, null), d.toolbarPosition),
            showToolsInTopBar = prefs.getBoolean(KEY_SHOW_TOOLS_IN_TOP_BAR, d.showToolsInTopBar),
            penColors = decodePenColors(prefs.getString(KEY_PEN_COLORS, null), d.penColors),
            lastColor = prefs.getInt(KEY_LAST_COLOR, d.lastColor),
            lastWidth = prefs.getFloat(KEY_LAST_WIDTH, d.lastWidth),
            highlighterColor = prefs.getInt(KEY_HIGHLIGHTER_COLOR, d.highlighterColor),
            highlighterWidth = prefs.getFloat(KEY_HIGHLIGHTER_WIDTH, d.highlighterWidth),
            shapeWidth = prefs.getFloat(KEY_SHAPE_WIDTH, d.shapeWidth),
            defaultShapeSlot = prefs.getInt(KEY_DEFAULT_SHAPE_SLOT, d.defaultShapeSlot),
            tableRows = prefs.getInt(KEY_TABLE_ROWS, d.tableRows),
            tableCols = prefs.getInt(KEY_TABLE_COLS, d.tableCols),
            tableHeader = prefs.getBoolean(KEY_TABLE_HEADER, d.tableHeader),
            shapeOrder = decodeToolNames(prefs.getString(KEY_SHAPE_ORDER, null), SHAPE_GROUP.tools),
            shapeHidden = decodeToolNames(prefs.getString(KEY_SHAPE_HIDDEN, null), SHAPE_GROUP.tools).toSet(),
            toolGroupSelections = decodeToolGroupSelections(prefs.getString(KEY_TOOL_GROUPS, null)),
            railOrder = decodeRailIds(prefs.getString(KEY_RAIL_ORDER, null)),
            railHidden = prefs.getString(KEY_RAIL_HIDDEN, null).let { raw ->
                if (raw != null) decodeRailIds(raw).toSet() else d.railHidden
            },
            topBarOrder = decodeTopBarIds(prefs.getString(KEY_TOP_BAR_ORDER, null)).ifEmpty { d.topBarOrder },
            topBarHidden = decodeTopBarIds(prefs.getString(KEY_TOP_BAR_HIDDEN, null)).toSet(),
            audioFolderUri = prefs.getString(KEY_AUDIO_FOLDER, d.audioFolderUri) ?: d.audioFolderUri,
            pageCounterVertical = enumOr(prefs.getString(KEY_PAGE_COUNTER_V, null), d.pageCounterVertical),
            pageCounterHorizontal =
                enumOr(prefs.getString(KEY_PAGE_COUNTER_H, null), d.pageCounterHorizontal),
            themeMode = enumOr(prefs.getString(KEY_THEME_MODE, null), d.themeMode),
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, d.dynamicColor),
            toolShortcutKeys = loadToolShortcutKeys(),
            colorShortcutKeys = loadColorShortcutKeys(),
            presets = decodeToolPresets(prefs.getString(KEY_PRESETS, null)),
            textImportLimitMb = prefs.getInt(KEY_TEXT_IMPORT_LIMIT, d.textImportLimitMb).coerceAtLeast(1),
            pdfCacheLimitMb = prefs.getInt(KEY_PDF_CACHE_LIMIT, d.pdfCacheLimitMb).coerceAtLeast(1),
            hasSeenOnboarding = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, d.hasSeenOnboarding),
        ).sanitized()
    }

    /**
     * The saved per-tool shortcuts, or — for an install predating the generic map — the one legacy
     * highlighter field migrated into it, so nobody loses a key they had already set.
     */
    private fun loadToolShortcutKeys(): Map<EditorTool, String> {
        prefs.getString(KEY_TOOL_SHORTCUTS, null)?.let { return decodeToolShortcuts(it) }
        val legacy = prefs.getString(KEY_HIGHLIGHTER_SHORTCUT, null) ?: DEFAULT_COLOR_KEY_4
        return legacy.takeIf { it.isNotEmpty() }?.let { mapOf(EditorTool.HIGHLIGHTER to it) }
            ?: emptyMap()
    }

    /**
     * The saved per-colour shortcuts, or — for an install predating the generic map — the three
     * legacy pen-colour fields migrated in, defaulting to the keys those fields shipped with.
     */
    private fun loadColorShortcutKeys(): Map<Int, String> {
        prefs.getString(KEY_COLOR_SHORTCUTS, null)?.let { return decodeColorShortcuts(it) }
        fun legacy(prefKey: String, color: Int, fallback: String): Pair<Int, String>? =
            (prefs.getString(prefKey, null) ?: fallback).takeIf { it.isNotEmpty() }?.let { color to it }
        return listOfNotNull(
            legacy(KEY_BLACK_PEN, XOPP_BLACK, DEFAULT_COLOR_KEY_1),
            legacy(KEY_RED_PEN, XOPP_RED, DEFAULT_COLOR_KEY_2),
            legacy(KEY_GREEN_PEN, XOPP_GREEN, DEFAULT_COLOR_KEY_3),
        ).toMap()
    }

    fun save(s: AppSettings) {
        val e = prefs.edit()
            .putFloat(KEY_DEFAULT_PAGE_WIDTH, s.defaultPageWidthPt.toFloat())
            .putFloat(KEY_DEFAULT_PAGE_HEIGHT, s.defaultPageHeightPt.toFloat())
            .putString(KEY_PEN_ERASER_TOGGLE, s.penEraserToggleKey)
            .putString(KEY_HAND_TOGGLE, s.handToggleKey)
            .putBoolean(KEY_FINGER_DRAWS, s.fingerDraws)
            .putString(KEY_BARREL, s.barrelAction.name)
            .putString(KEY_BARREL_DOUBLE, s.barrelDoubleAction.name)
            .putBoolean(KEY_HOVER, s.showHover)
            .putBoolean(KEY_PRESSURE_ENABLED, s.pressureEnabled)
            .putFloat(KEY_PRESSURE_MULTIPLIER, s.pressureMultiplier)
            .putFloat(KEY_MINIMUM_PRESSURE, s.minimumPressure)
            .putString(KEY_STROKE_PRECISION, s.strokePrecision.name)
            .putBoolean(KEY_RECOGNIZE_SHAPES, s.recognizeShapes)
            .putInt(KEY_PAGE_COLUMNS, s.pageColumns)
            .putBoolean(KEY_SNAP_GRID, s.snapToGrid)
            .putBoolean(KEY_SNAP_ROTATION, s.snapRotation)
            .putString(KEY_GUIDE_KIND, s.guideKind.name)
        s.penWidths.forEachIndexed { i, w -> e.putFloat(keyPenWidth(i), w) }
        e.putInt(KEY_CUSTOM_COLOR, s.customColor)
        e.putString(KEY_DEFAULT_TOOL, s.defaultTool.name)
        e.putFloat(KEY_MOMENTUM, s.momentum)
        e.putString(KEY_MOMENTUM_CURVE, s.momentumCurve.name)
        e.putFloat(KEY_PAN_SENSITIVITY, s.panSensitivity)
        e.putString(KEY_TOOLBAR_POSITION, s.toolbarPosition.name)
        e.putBoolean(KEY_SHOW_TOOLS_IN_TOP_BAR, s.showToolsInTopBar)
        e.putString(KEY_PEN_COLORS, encodePenColors(s.penColors))
        e.putInt(KEY_LAST_COLOR, s.lastColor)
        e.putFloat(KEY_LAST_WIDTH, s.lastWidth)
        e.putInt(KEY_HIGHLIGHTER_COLOR, s.highlighterColor)
        e.putFloat(KEY_HIGHLIGHTER_WIDTH, s.highlighterWidth)
        e            .putFloat(KEY_SHAPE_WIDTH, s.shapeWidth)
            .putInt(KEY_DEFAULT_SHAPE_SLOT, s.defaultShapeSlot)
            .putInt(KEY_TABLE_ROWS, s.tableRows)
            .putInt(KEY_TABLE_COLS, s.tableCols)
            .putBoolean(KEY_TABLE_HEADER, s.tableHeader)
            .putString(KEY_SHAPE_ORDER, encodeToolNames(s.shapeOrder))
            .putString(KEY_SHAPE_HIDDEN, encodeToolNames(s.shapeHidden))
        e.putString(KEY_TOOL_GROUPS, encodeToolGroupSelections(s.toolGroupSelections))
        e.putString(KEY_RAIL_ORDER, encodeRailIds(s.railOrder))
        e.putString(KEY_RAIL_HIDDEN, encodeRailIds(s.railHidden))
        e.putString(KEY_TOP_BAR_ORDER, encodeTopBarIds(s.topBarOrder))
        e.putString(KEY_TOP_BAR_HIDDEN, encodeTopBarIds(s.topBarHidden))
        e.putString(KEY_AUDIO_FOLDER, s.audioFolderUri)
        e.putString(KEY_PAGE_COUNTER_V, s.pageCounterVertical.name)
        e.putString(KEY_PAGE_COUNTER_H, s.pageCounterHorizontal.name)
        e.putString(KEY_THEME_MODE, s.themeMode.name)
        e.putBoolean(KEY_DYNAMIC_COLOR, s.dynamicColor)
        e.putString(KEY_TOOL_SHORTCUTS, encodeToolShortcuts(s.toolShortcutKeys))
        e.putString(KEY_COLOR_SHORTCUTS, encodeColorShortcuts(s.colorShortcutKeys))
        // The legacy per-shortcut keys are gone once the generic maps exist, so the migration above
        // runs exactly once. The recents row and the fill toggle are gone entirely; dropping their
        // pref keys keeps a stale value from lingering in a file nobody reads any more.
        e.remove(KEY_BLACK_PEN)
        e.remove(KEY_RED_PEN)
        e.remove(KEY_GREEN_PEN)
        e.remove(KEY_HIGHLIGHTER_SHORTCUT)
        e.remove("recent_colors")
        e.remove("fill_enabled")
        e.remove("fill_alpha")
        e.putString(KEY_PRESETS, encodeToolPresets(s.presets))
        e.putInt(KEY_TEXT_IMPORT_LIMIT, s.textImportLimitMb)
        e.putInt(KEY_PDF_CACHE_LIMIT, s.pdfCacheLimitMb)
        e.putBoolean(KEY_HAS_SEEN_ONBOARDING, s.hasSeenOnboarding)
        e.apply()
    }

    private companion object {

        const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"

        /** Default A4 width in points. */
        const val DEFAULT_PAGE_WIDTH_PT = 595.276
        /** Default A4 height in points. */
        const val DEFAULT_PAGE_HEIGHT_PT = 841.89

        /** Factory shortcut keys for the three legacy pen colours and the highlighter. */
        const val DEFAULT_COLOR_KEY_1 = "1"
        const val DEFAULT_COLOR_KEY_2 = "2"
        const val DEFAULT_COLOR_KEY_3 = "3"
        const val DEFAULT_COLOR_KEY_4 = "4"

        // Legacy per-shortcut keys: read once to migrate into [KEY_TOOL_SHORTCUTS] / [KEY_COLOR_SHORTCUTS].
        const val KEY_BLACK_PEN = "black_pen_key"
        const val KEY_RED_PEN = "red_pen_key"
        const val KEY_GREEN_PEN = "green_pen_key"
        const val KEY_HIGHLIGHTER_SHORTCUT = "highlighter_key"
        const val KEY_DEFAULT_PAGE_WIDTH = "default_page_width_pt"
        const val KEY_DEFAULT_PAGE_HEIGHT = "default_page_height_pt"
        const val KEY_PEN_ERASER_TOGGLE = "pen_eraser_toggle_key"

        const val KEY_HAND_TOGGLE = "hand_toggle_key"
        const val KEY_FINGER_DRAWS = "finger_draws"
        const val KEY_BARREL = "barrel_action"
        const val KEY_BARREL_DOUBLE = "barrel_double_action"
        const val KEY_HOVER = "show_hover"
        const val KEY_PRESSURE_ENABLED = "pressure_enabled"
        const val KEY_PRESSURE_MULTIPLIER = "pressure_multiplier"
        const val KEY_MINIMUM_PRESSURE = "minimum_pressure"
        const val KEY_STROKE_PRECISION = "stroke_precision"
        const val KEY_RECOGNIZE_SHAPES = "recognize_shapes"
        const val KEY_PAGE_COLUMNS = "page_columns"
        const val KEY_SNAP_GRID = "snap_to_grid"
        const val KEY_SNAP_ROTATION = "snap_rotation"
        const val KEY_GUIDE_KIND = "guide_kind"
        const val KEY_CUSTOM_COLOR = "custom_color"
        const val KEY_DEFAULT_TOOL = "default_tool"
        // Float-typed since the parameterized control replaced the old discrete enum; a fresh key
        // avoids a ClassCastException on any pref still holding the old enum-name string.
        const val KEY_MOMENTUM = "momentum_factor"
        const val KEY_MOMENTUM_CURVE = "momentum_curve"
        const val KEY_PAN_SENSITIVITY = "pan_sensitivity"
        const val KEY_TOOLBAR_POSITION = "toolbar_position"
        const val KEY_SHOW_TOOLS_IN_TOP_BAR = "show_tools_in_top_bar"
        const val KEY_PEN_COLORS = "pen_colors"
        const val KEY_LAST_COLOR = "last_color"
        const val KEY_LAST_WIDTH = "last_width"
        const val KEY_HIGHLIGHTER_COLOR = "highlighter_color"
        const val KEY_HIGHLIGHTER_WIDTH = "highlighter_width"
        const val KEY_SHAPE_WIDTH = "shape_width"
        const val KEY_DEFAULT_SHAPE_SLOT = "default_shape_slot"
        const val KEY_TABLE_ROWS = "table_rows"
        const val KEY_TABLE_COLS = "table_cols"
        const val KEY_TABLE_HEADER = "table_header"
        const val KEY_SHAPE_ORDER = "shape_order"
        const val KEY_SHAPE_HIDDEN = "shape_hidden"
        const val KEY_TOOL_GROUPS = "tool_group_selections"
        const val KEY_RAIL_ORDER = "rail_order"
        const val KEY_RAIL_HIDDEN = "rail_hidden"
        const val KEY_TOP_BAR_ORDER = "top_bar_order"
        const val KEY_TOP_BAR_HIDDEN = "top_bar_hidden"
        const val KEY_AUDIO_FOLDER = "audio_folder_uri"
        const val KEY_PAGE_COUNTER_V = "page_counter_vertical"
        const val KEY_PAGE_COUNTER_H = "page_counter_horizontal"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_TOOL_SHORTCUTS = "tool_shortcuts"
        const val KEY_COLOR_SHORTCUTS = "color_shortcuts"
        const val KEY_PRESETS = "tool_presets"
        const val KEY_TEXT_IMPORT_LIMIT = "text_import_limit_mb"
        const val KEY_PDF_CACHE_LIMIT = "pdf_cache_limit_mb"

        /** Per-slot SharedPreferences key for the [i]th configurable pen width. */
        fun keyPenWidth(i: Int): String = "pen_width_$i"

        /** Parse an enum by name, falling back to [default] for missing/unknown values. */
        inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
    }
}

/**
 * Encode per-tool shortcut keys for `SharedPreferences`: `TOOL:key` pairs, comma-separated.
 *
 * Only non-empty keys are written, so clearing a field drops its entry rather than persisting a
 * blank that would shadow a future default.
 */
fun encodeToolShortcuts(keys: Map<EditorTool, String>): String =
    keys.entries.filter { it.value.isNotEmpty() }
        .joinToString(",") { "${it.key.name}:${it.value}" }

/**
 * Parse what [encodeToolShortcuts] wrote, dropping entries whose tool no longer exists and any with
 * an empty key, so a stale pref degrades to "no shortcut" rather than a crash.
 */
fun decodeToolShortcuts(raw: String?): Map<EditorTool, String> =
    raw?.split(',')?.mapNotNull { entry ->
        val (name, key) = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val tool = runCatching { enumValueOf<EditorTool>(name.trim()) }.getOrNull()
            ?: return@mapNotNull null
        if (key.isEmpty()) null else tool to key
    }?.toMap() ?: emptyMap()

/**
 * Encode the pen palette for `SharedPreferences`: the ARGB values, comma-separated, written by the
 * store. Kept separate from [encodeColorShortcuts] so the two lists can diverge in what they filter.
 */
fun encodePenColors(colors: Collection<Int>): String = colors.joinToString(",") { it.toString() }

/**
 * Parse what [encodePenColors] wrote, falling back to [fallback] when nothing usable is stored — a
 * blanked or corrupt pref must not leave the colour pickers with no swatches at all.
 * The user's own colours are kept, not just the named desktop ones: a swatch added under
 * **Settings → Colors** has to come back on the next launch, and it has to survive a JSON backup,
 * which carries the palette verbatim. Only duplicates and the over-[AppSettings.MAX_PEN_COLORS]
 * tail are dropped; entries are normalised to opaque ARGB rather than judged against a name table.
 */
fun decodePenColors(raw: String?, fallback: List<Int>): List<Int> =
    raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
        ?.map { it or 0xFF000000.toInt() }
        ?.distinct()
        ?.take(AppSettings.MAX_PEN_COLORS)
        ?.takeIf { it.isNotEmpty() }
        ?: fallback

/**
 * Encode per-colour shortcut keys for `SharedPreferences`: `argb:key` pairs, comma-separated. Only
 * non-empty keys are written, mirroring [encodeToolShortcuts].
 */
fun encodeColorShortcuts(keys: Map<Int, String>): String =
    keys.entries.filter { it.value.isNotEmpty() }
        .joinToString(",") { "${it.key}:${it.value}" }

/**
 * Parse what [encodeColorShortcuts] wrote, dropping unparsable colours and empty keys. The ARGB
 * value is kept verbatim (it may be negative as a signed Int), so a round-trip is lossless.
 */
fun decodeColorShortcuts(raw: String?): Map<Int, String> =
    raw?.split(',')?.mapNotNull { entry ->
        val (argb, key) = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val color = argb.trim().toIntOrNull() ?: return@mapNotNull null
        if (key.isEmpty()) null else color to key
    }?.toMap() ?: emptyMap()
