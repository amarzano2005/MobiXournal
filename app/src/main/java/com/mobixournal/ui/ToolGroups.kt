package com.mobixournal.ui

import com.mobixournal.render.ShapeBuilder

/**
 * A toolbar slot that stands for several related tools. The rail shows **one** button per group —
 * the group's currently selected tool. Tapping the button activates that tool; long-pressing opens a
 * picker that changes which member the slot stands for (see `ToolGroupButton` in [SideToolbar.kt]).
 *
 * Selections are persisted per group id in [AppSettings.toolGroupSelections], so the rail looks the
 * same after the app is closed and reopened.
 */
data class ToolGroup(
    /** Stable key — the SharedPreferences identity of this group's selection. Never rename. */
    val id: String,
    /** Human-readable name, shown as the picker's heading. */
    val label: String,
    /** The group's members, in picker order; the first is the factory-default selection. */
    val tools: List<EditorTool>,
    /**
     * Which members share a **picker row**, in display order — the transistor pair, the diode family,
     * the two sources. Empty means the picker simply wraps [tools] [PICKER_ROW_SIZE] to a row, which
     * is what a group whose members have no kinship worth expressing wants.
     */
    val pickerLayout: List<List<EditorTool>> = emptyList(),
)

/**
 * How many members share a picker row when their group declares no [ToolGroup.pickerLayout] of its
 * own. Three fits a phone's menu width with room for a two-line label per tool.
 */
const val PICKER_ROW_SIZE = 3

/**
 * The rows a group's picker lays [members] out in: [layout] first — so members that belong together
 * share a row instead of each taking a full row of its own — then every member the layout doesn't
 * name, wrapped [PICKER_ROW_SIZE] to a row. That tail is what keeps a newly added tool (or the Shapes
 * slot's user-ordered list, which declares no layout) from going missing from the picker. Members that
 * aren't shown and repeats in [layout] are dropped.
 */
fun pickerRows(
    layout: List<List<EditorTool>>,
    members: List<EditorTool>,
): List<List<EditorTool>> {
    val shown = members.toHashSet()
    val placed = HashSet<EditorTool>()
    val rows = ArrayList<List<EditorTool>>()
    for (row in layout) {
        val kept = row.filter { it in shown && placed.add(it) }
        if (kept.isNotEmpty()) rows.add(kept)
    }
    rows.addAll(members.filterNot { it in placed }.chunked(PICKER_ROW_SIZE))
    return rows
}

/**
 * How many geometric variants share a **variant picker** row. Two keeps the longest kind names ("Right-angled") on one
 * line and leaves no lone straggler: the triangle's four kinds fall 2 + 2 and the trapezoid's three fall 2 + 1.
 */
const val VARIANT_ROW_SIZE = 2

/**
 * The rows a **variant picker** lays [count] variants out in — the same row language [pickerRows] gives a tool group's
 * members, so a figure's own submenu (the triangle's kinds, the trapezoid's) reads like the tool pickers instead of a
 * list of full-width rows. Variants are a plain sequence with no kinship to express, so they just wrap
 * [VARIANT_ROW_SIZE] to a row.
 */
fun variantRows(count: Int): List<List<Int>> = (0 until maxOf(count, 0)).chunked(VARIANT_ROW_SIZE)

/**
 * The rail's tool slots, in display order: **writing tools only**. A group with a single member (pan,
 * vertical space) is just a plain button with nothing to pick.
 *
 * The geometric figures and the STEM families (circuits, logic, chemistry, table) are **not** rail
 * slots: their one home is the Secondary Toolbar in the top bar, and offering them here as well was
 * two rows of the same buttons on one screen ([SECONDARY_TOOL_GROUPS] is where they live).
 * `settings.railOrder` therefore names only the ids below — anything else it still carries from an
 * older install is dropped by `decodeRailIds`, so no stale preference can bring a duplicated slot back.
 *
 * Some slots use membership to express what used to be a separate mode menu: the eraser's
 * partial-vs-whole-stroke choice and the marquee's rectangle-vs-lasso-vs-text choice are each just
 * another member of their group, picked from the slot's menu (`ToolGroupButton` in SideToolbar.kt).
 * The menu lays those members out in rows ([pickerRows]), so related tools sit side by side rather
 * than each taking a row of its own; [ToolGroup.pickerLayout] says which ones belong together.
 */
val TOOL_GROUPS: List<ToolGroup> = listOf(
    ToolGroup("pen", "Pen", listOf(EditorTool.PEN)),
    ToolGroup("highlighter", "Highlighter", listOf(EditorTool.HIGHLIGHTER)),
    ToolGroup("eraser", "Eraser", listOf(EditorTool.ERASER_WHOLE, EditorTool.ERASER)),
    ToolGroup("pan", "Pan", listOf(EditorTool.HAND)),
    ToolGroup(
        "select", "Select",
        listOf(EditorTool.SELECT, EditorTool.LASSO_SELECT),
        pickerLayout = listOf(
            listOf(EditorTool.SELECT, EditorTool.LASSO_SELECT),
        ),
    ),
    // Text authoring lives in a slot of its own (below), so Insert is only about pictures.
    ToolGroup("insert", "Insert", listOf(EditorTool.IMAGE)),
    ToolGroup("text", "Text", listOf(EditorTool.TEXT, EditorTool.TEXIMAGE)),
    ToolGroup("vspace", "Vertical space", listOf(EditorTool.VERTICAL_SPACE)),
)

/**
 * The STEM families the **Secondary Toolbar** in the top bar draws, one slot per family with its
 * picker: the figures, the arrows, the table, the two circuit halves, logic gates and chemistry.
 *
 * They are not rail positions ([TOOL_GROUPS] is writing tools only) but they are still tool *groups*:
 * the top bar resolves a `circuit`/`logic`/`chemistry`/`arrow` button through [secondaryToolGroup], and
 * the family's members are what that button's menu offers.
 */
val SECONDARY_TOOL_GROUPS: List<ToolGroup> = listOf(
    ToolGroup("line", "Line", listOf(EditorTool.LINE)),
    ToolGroup("rectangle", "Rectangle", listOf(EditorTool.RECTANGLE)),
    ToolGroup(
        "shape", "Shapes",
        listOf(
            EditorTool.ELLIPSE,
            EditorTool.TRIANGLE, EditorTool.RHOMBUS, EditorTool.TRAPEZOID,
            EditorTool.COORDINATE_AXIS,
        ),
    ),
    // The polygons share one slot. The **generic** polygon leads it: it draws a regular figure of
    // however many sides it is set to (Settings ▸ Figures), so it covers the whole family; the
    // pentagon and the hexagon stay behind it as the two presets a hand reaches for most in a
    // figure that is set up once and drawn many times. A square is not here — the rectangle draws
    // one, and a second square-shaped button only made the row longer.
    ToolGroup(
        "polygons", "Polygons",
        listOf(EditorTool.POLYGON, EditorTool.PENTAGON, EditorTool.HEXAGON),
    ),
    ToolGroup(
        "arrow", "Arrows",
        listOf(EditorTool.ARROW, EditorTool.DOUBLE_ARROW, EditorTool.DIMENSION),
    ),
    ToolGroup("table", "Table", listOf(EditorTool.TABLE)),
    ToolGroup(
        "circuit", "Passive circuits",
        listOf(
            EditorTool.RESISTOR, EditorTool.CAPACITOR, EditorTool.INDUCTOR,
            EditorTool.GROUND, EditorTool.SWITCH_OPEN, EditorTool.SWITCH_CLOSED,
            EditorTool.JUNCTION,
        ),
        pickerLayout = listOf(
            listOf(EditorTool.RESISTOR, EditorTool.CAPACITOR),
            listOf(EditorTool.INDUCTOR, EditorTool.GROUND),
            listOf(EditorTool.SWITCH_OPEN, EditorTool.SWITCH_CLOSED),
            listOf(EditorTool.JUNCTION),
        ),
    ),
    ToolGroup(
        "circuit_active", "Active circuits",
        listOf(
            EditorTool.DIODE, EditorTool.LED, EditorTool.ZENER_DIODE,
            EditorTool.OPAMP, EditorTool.BJT_NPN, EditorTool.BJT_PNP,
            EditorTool.DC_SOURCE, EditorTool.CURRENT_SOURCE,
            EditorTool.TRANSFORMER,
        ),
        // The diode family together; the amplifiers and the two transistors (which are read as a
        // pair) together; then the two sources, with the transformer in the "energy" row.
        pickerLayout = listOf(
            listOf(EditorTool.DIODE, EditorTool.LED, EditorTool.ZENER_DIODE),
            listOf(EditorTool.OPAMP, EditorTool.BJT_NPN, EditorTool.BJT_PNP),
            listOf(EditorTool.DC_SOURCE, EditorTool.CURRENT_SOURCE, EditorTool.TRANSFORMER),
        ),
    ),
    ToolGroup(
        "logic", "Logic gates",
        listOf(
            EditorTool.AND_GATE, EditorTool.OR_GATE, EditorTool.NOT_GATE,
            EditorTool.NAND_GATE, EditorTool.NOR_GATE, EditorTool.XOR_GATE,
            EditorTool.XNOR_GATE, EditorTool.BUFFER_GATE,
        ),
        // Each gate beside its inverted twin; the buffer joins the NOT gate on the "plain gate" row.
        pickerLayout = listOf(
            listOf(EditorTool.AND_GATE, EditorTool.NAND_GATE),
            listOf(EditorTool.OR_GATE, EditorTool.NOR_GATE),
            listOf(EditorTool.XOR_GATE, EditorTool.XNOR_GATE),
            listOf(EditorTool.NOT_GATE, EditorTool.BUFFER_GATE),
        ),
    ),
)

/** Every tool group in the app — the rail's and the Secondary Toolbar's. */
val ALL_TOOL_GROUPS: List<ToolGroup> = TOOL_GROUPS + SECONDARY_TOOL_GROUPS

/** The Secondary Toolbar's group behind [id], or null when that button is not a family. */
fun secondaryToolGroup(id: String): ToolGroup? = SECONDARY_TOOL_GROUPS.firstOrNull { it.id == id }

/** The group [tool] belongs to, or null if it is somehow ungrouped. */
fun groupOf(tool: EditorTool): ToolGroup? = ALL_TOOL_GROUPS.firstOrNull { tool in it.tools }

/**
 * All geometric figure tools available in the app (including simple line and rectangle).
 * These are configured under Settings -> Figures and displayed in the toolbars.
 */
val FIGURE_TOOLS: List<EditorTool> = listOf(
    EditorTool.LINE,
    EditorTool.RECTANGLE,
    EditorTool.ELLIPSE,
    EditorTool.TRIANGLE,
    EditorTool.RHOMBUS,
    EditorTool.TRAPEZOID,
    EditorTool.POLYGON,
    EditorTool.PENTAGON,
    EditorTool.HEXAGON,
    EditorTool.COORDINATE_AXIS,
)

/**
 * The side counts the polygon slot's picker offers, in order — the builder's own bounds, so the menu,
 * the stored setting and the drawn figure cannot disagree about what a polygon may be.
 */
val POLYGON_SIDE_CHOICES: List<Int> =
    (ShapeBuilder.MIN_POLYGON_SIDES..ShapeBuilder.MAX_POLYGON_SIDES).toList()

/** Where [sides] sits in [POLYGON_SIDE_CHOICES]; an out-of-range count answers the nearest end. */
fun polygonSideIndex(sides: Int): Int =
    POLYGON_SIDE_CHOICES.indexOf(sides.coerceIn(ShapeBuilder.MIN_POLYGON_SIDES, ShapeBuilder.MAX_POLYGON_SIDES))
        .coerceAtLeast(0)

/** The side count at [index], clamped: a picker row can only ever name one of the choices. */
fun polygonSidesAt(index: Int): Int = POLYGON_SIDE_CHOICES[index.coerceIn(POLYGON_SIDE_CHOICES.indices)]

/** The tool names of all geometric figure tools. */
val ALL_FIGURE_TOOL_NAMES: Set<String> = FIGURE_TOOLS.map { it.name }.toSet()

/**
 * The figure tools in the user's [order]: the names it lists first, then any member it
 * omits appended in factory order, so newly introduced figures appear rather than vanishing.
 */
fun orderedShapeTools(order: List<String>): List<EditorTool> {
    val all = FIGURE_TOOLS
    val byName = all.associateBy { it.name }
    val listed = order.distinct().mapNotNull { byName[it] }
    return listed + all.filterNot { it in listed }
}

/** Encode a tool-name list for SharedPreferences: comma-separated. */
fun encodeToolNames(names: Collection<String>): String = names.joinToString(",")

/**
 * Parse what [encodeToolNames] wrote, keeping only names that still name a tool in [known] — a stale
 * or renamed figure degrades to the factory order instead of pointing at nothing.
 */
fun decodeToolNames(raw: String?, known: Collection<EditorTool>): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val valid = known.map { it.name }.toSet()
    return raw.split(',').map { it.trim() }.filter { it in valid }.distinct()
}

/**
 * [ids] with the item at [index] moved by [delta] places. Out-of-range moves are no-ops, so the
 * settings list can run the same drag reordering the rail does.
 */
fun moveId(ids: List<String>, index: Int, delta: Int): List<String> {
    val list = ids.toMutableList()
    val to = index + delta
    if (index !in list.indices || to !in list.indices) return list
    list.add(to, list.removeAt(index))
    return list
}

/**
 * The tool a freshly opened document should start in: [defaultTool]'s slot, faced with whatever the
 * user last picked for it. Without this the rail would open showing a persisted choice (highlighter,
 * say) while the canvas was really in the group's first tool.
 */
fun startingTool(defaultTool: EditorTool, selections: Map<String, EditorTool>): EditorTool =
    groupOf(defaultTool)?.selected(selections) ?: defaultTool

/**
 * The eraser the rail's **Eraser** slot currently stands for: whole-stroke unless the user picked the
 * partial eraser from that slot's menu.
 *
 * Shared by everything that switches *to the eraser* (the barrel button's toggle and the keyboard
 * shortcut) so they all land on the eraser the rail is showing rather than on a fixed one. The
 * whole-stroke default comes from the group's own order, and matches desktop Xournal++'s
 * delete-the-whole-stroke eraser, which is the one a toggle is usually reached for.
 */
fun preferredEraser(selections: Map<String, EditorTool>): EditorTool =
    groupOf(EditorTool.ERASER_WHOLE)?.selected(selections) ?: EditorTool.ERASER_WHOLE

/**
 * The tool this group's slot currently stands for: the persisted choice when it is still a member of
 * the group, otherwise the group's first tool. Guarding on membership keeps a stale or hand-edited
 * preference from pointing a slot at a tool that has since moved groups.
 */
fun ToolGroup.selected(selections: Map<String, EditorTool>): EditorTool =
    selections[id]?.takeIf { it in tools } ?: tools.first()

/**
 * [selections] with this group's slot pointed at [tool]. A tool that isn't a member is ignored, so a
 * bad call can't corrupt the persisted map.
 */
fun ToolGroup.withSelection(
    selections: Map<String, EditorTool>,
    tool: EditorTool,
): Map<String, EditorTool> =
    if (tool in tools) selections + (id to tool) else selections

/** Encode a selection map for SharedPreferences: `group:TOOL` pairs, comma-separated. */
fun encodeToolGroupSelections(selections: Map<String, EditorTool>): String =
    selections.entries.joinToString(",") { "${it.key}:${it.value.name}" }

/**
 * Parse what [encodeToolGroupSelections] wrote, dropping entries whose group or tool no longer
 * exists (or is no longer a member) so a stale pref degrades to the defaults rather than a crash.
 */
fun decodeToolGroupSelections(raw: String?): Map<String, EditorTool> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(',').mapNotNull { entry ->
        val (id, name) = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val group = ALL_TOOL_GROUPS.firstOrNull { it.id == id.trim() } ?: return@mapNotNull null
        val tool = runCatching { enumValueOf<EditorTool>(name.trim()) }.getOrNull() ?: return@mapNotNull null
        if (tool in group.tools) group.id to tool else null
    }.toMap()
}
