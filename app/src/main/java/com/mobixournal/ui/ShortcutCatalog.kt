package com.mobixournal.ui

import java.util.Locale

/** Filter pills shown at the top of [ShortcutsSection]. */
internal enum class ShortcutFilterTab(val label: String) {
    ALL("All"),
    BOUND("Bound"),
    TOGGLES("Toggles"),
    TOOLS("Tools"),
    COLORS("Colors"),
}

/** Logical families organizing tool shortcuts in reading order, matching the toolbar groups. */
internal enum class ShortcutToolFamily(
    val title: String,
    val tools: List<EditorTool>,
    val collapsible: Boolean = false,
) {
    INK(
        title = "Ink",
        tools = listOf(
            EditorTool.PEN,
            EditorTool.HIGHLIGHTER,
            EditorTool.ERASER,
            EditorTool.ERASER_WHOLE,
        ),
    ),
    SELECTION(
        title = "Selection & navigation",
        tools = listOf(
            EditorTool.HAND,
            EditorTool.SELECT,
            EditorTool.LASSO_SELECT,
            EditorTool.TEXT_SELECT,
            EditorTool.BG_SELECT,
            EditorTool.VERTICAL_SPACE,
        ),
    ),
    INSERT(
        title = "Insert & media",
        tools = listOf(
            EditorTool.TEXT,
            EditorTool.TEXIMAGE,
            EditorTool.IMAGE,
            EditorTool.PLOT,
            EditorTool.PLAY_OBJECT,
        ),
    ),
    SHAPES(
        title = "Shapes & diagrams",
        tools = listOf(
            EditorTool.LINE,
            EditorTool.SPLINE,
            EditorTool.ARROW,
            EditorTool.DOUBLE_ARROW,
            EditorTool.COORDINATE_AXIS,
            EditorTool.RECTANGLE,
            EditorTool.ELLIPSE,
            EditorTool.TRIANGLE,
            EditorTool.TRAPEZOID,
            EditorTool.POLYGON,
            EditorTool.TABLE,
            EditorTool.DIMENSION,
        ),
    ),
    CIRCUITS(
        title = "Circuits & logic",
        tools = listOf(
            EditorTool.RESISTOR,
            EditorTool.CAPACITOR,
            EditorTool.INDUCTOR,
            EditorTool.GROUND,
            EditorTool.DIODE,
            EditorTool.LED,
            EditorTool.ZENER_DIODE,
            EditorTool.OPAMP,
            EditorTool.BJT_NPN,
            EditorTool.BJT_PNP,
            EditorTool.DC_SOURCE,
            EditorTool.CURRENT_SOURCE,
            EditorTool.AND_GATE,
            EditorTool.OR_GATE,
            EditorTool.NOT_GATE,
            EditorTool.NAND_GATE,
            EditorTool.NOR_GATE,
            EditorTool.XOR_GATE,
            EditorTool.XNOR_GATE,
            EditorTool.BUFFER_GATE,
            EditorTool.SWITCH_OPEN,
            EditorTool.SWITCH_CLOSED,
            EditorTool.TRANSFORMER,
            EditorTool.JUNCTION,
        ),
        collapsible = true,
    ),
}

/**
 * Legacy enum entries kept only so saved preferences continue to decode cleanly. Unbound retired
 * tools are hidden from the Settings list; any still bound remain accessible via [boundRetiredTools].
 */
internal val RETIRED_SHORTCUT_TOOLS: Set<EditorTool> = setOf(
    EditorTool.SQUARE,
    EditorTool.CHEM_BOND_SINGLE,
    EditorTool.CHEM_BOND_DOUBLE,
    EditorTool.CHEM_BOND_TRIPLE,
    EditorTool.CHEM_BENZENE,
)

/** Active tool families paired with their tools, preserving the catalog order. */
internal fun shortcutToolGroups(): List<Pair<ShortcutToolFamily, List<EditorTool>>> =
    ShortcutToolFamily.entries.map { family -> family to family.tools }

/** Legacy retired tools that currently carry a bound shortcut key. */
internal fun boundRetiredTools(bound: Map<EditorTool, String>): List<EditorTool> =
    EditorTool.entries.filter { it in RETIRED_SHORTCUT_TOOLS && !bound[it].isNullOrBlank() }

/** Total number of active keyboard/ExpressKey shortcuts across toggles, tools, and colors. */
internal fun totalBoundShortcuts(settings: AppSettings): Int {
    val toggleCount = toggleShortcutValues(settings).count { it.isNotBlank() }
    val toolCount = settings.toolShortcutKeys.values.count { it.isNotBlank() }
    val colorCount = settings.colorShortcutKeys.values.count { it.isNotBlank() }
    return toggleCount + toolCount + colorCount
}

/** Normalized uppercase keys bound to two or more actions (toggles, tools, or colors). */
internal fun duplicateShortcutKeys(settings: AppSettings): Set<String> {
    val counts = LinkedHashMap<String, Int>()
    val allKeys = toggleShortcutValues(settings) +
        settings.toolShortcutKeys.values +
        settings.colorShortcutKeys.values
    for (raw in allKeys) {
        val norm = raw.trim().uppercase(Locale.US)
        if (norm.isNotEmpty()) {
            counts[norm] = (counts[norm] ?: 0) + 1
        }
    }
    return counts.filterValues { it >= 2 }.keys
}

/** True when [label] or the bound [key] matches [query], or when [query] is blank. */
internal fun shortcutMatches(label: String, key: String, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return label.contains(needle, ignoreCase = true) ||
        (key.isNotBlank() && key.contains(needle, ignoreCase = true))
}

/** Restores the five toggle keys to defaults and clears all custom tool and color shortcuts. */
internal fun resetShortcutsToDefaults(settings: AppSettings): AppSettings {
    val defaults = AppSettings()
    return settings.copy(
        penEraserToggleKey = defaults.penEraserToggleKey,
        handToggleKey = defaults.handToggleKey,
        selectToggleKey = defaults.selectToggleKey,
        fullPageToggleKey = defaults.fullPageToggleKey,
        ringShortcutKey = defaults.ringShortcutKey,
        toolShortcutKeys = emptyMap(),
        colorShortcutKeys = emptyMap(),
    )
}

/**
 * The colour-shortcut rows: one per swatch in the palette, in palette order, each carrying the key
 * it is bound to (empty while unassigned). Duplicates are dropped cleanly.
 */
internal fun colourShortcutRows(colors: List<Int>, keys: Map<Int, String>): List<Pair<Int, String>> =
    colors.distinct().map { color -> color to keys[color].orEmpty() }

private fun toggleShortcutValues(settings: AppSettings): List<String> = listOf(
    settings.penEraserToggleKey,
    settings.handToggleKey,
    settings.selectToggleKey,
    settings.fullPageToggleKey,
    settings.ringShortcutKey,
)
