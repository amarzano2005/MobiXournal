package com.mobixournal.ui

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
)

/**
 * The rail's tool slots, in display order. Every [EditorTool] belongs to exactly one group; a group
 * with a single member (line, rectangle, pan) is just a plain button with nothing to pick.
 *
 * Line and Rectangle get a slot of their own because they are the shapes users reach for most; every
 * other figure shares one Shapes slot, whose face defaults to the **ellipse** (the circle) — the
 * figure a hand-drawn shape most often means.
 *
 * Some slots use membership to express what used to be a separate mode menu: the eraser's
 * partial-vs-whole-stroke choice and the marquee's rectangle-vs-lasso-vs-text choice are each just
 * another member of their group, picked from the slot's menu (`ToolGroupButton` in SideToolbar.kt).
 */
val TOOL_GROUPS: List<ToolGroup> = listOf(
    ToolGroup("pen", "Pen", listOf(EditorTool.PEN)),
    ToolGroup("highlighter", "Highlighter", listOf(EditorTool.HIGHLIGHTER)),
    ToolGroup("eraser", "Eraser", listOf(EditorTool.ERASER_WHOLE, EditorTool.ERASER)),
    ToolGroup("line", "Line", listOf(EditorTool.LINE)),
    ToolGroup("rectangle", "Rectangle", listOf(EditorTool.RECTANGLE)),
    ToolGroup(
        "shape", "Shapes",
        listOf(
            EditorTool.ELLIPSE,
            // The STEM figures (see [ShapeKind] / [ShapeBuilder]).
            EditorTool.TRIANGLE, EditorTool.SQUARE, EditorTool.RHOMBUS,
            EditorTool.PENTAGON, EditorTool.HEXAGON, EditorTool.STAR,
            EditorTool.COORDINATE_AXIS, EditorTool.SPLINE,
        ),
    ),
    ToolGroup(
        "arrow", "Arrows",
        listOf(EditorTool.ARROW, EditorTool.DOUBLE_ARROW),
    ),
    ToolGroup("table", "Table", listOf(EditorTool.TABLE)),
    ToolGroup(
        "circuit", "Circuits",
        listOf(
            EditorTool.RESISTOR, EditorTool.CAPACITOR, EditorTool.INDUCTOR,
            EditorTool.GROUND,
        ),
    ),
    ToolGroup(
        "logic", "Logic gates",
        listOf(
            EditorTool.AND_GATE, EditorTool.OR_GATE, EditorTool.NOT_GATE,
            EditorTool.NAND_GATE, EditorTool.NOR_GATE, EditorTool.XOR_GATE,
            EditorTool.XNOR_GATE,
        ),
    ),
    ToolGroup("pan", "Pan", listOf(EditorTool.HAND)),
    ToolGroup(
        "select", "Select",
        listOf(
            EditorTool.SELECT, EditorTool.LASSO_SELECT, EditorTool.TEXT_SELECT,
            EditorTool.BG_SELECT,
        ),
    ),
    // Text authoring lives in a slot of its own (below), so Insert is only about pictures.
    ToolGroup("insert", "Insert", listOf(EditorTool.IMAGE)),
    ToolGroup("text", "Text", listOf(EditorTool.TEXT, EditorTool.TEXIMAGE)),
    ToolGroup("vspace", "Vertical space", listOf(EditorTool.VERTICAL_SPACE)),
    ToolGroup("play", "Play object", listOf(EditorTool.PLAY_OBJECT)),
)

/** The group [tool] belongs to, or null if it is somehow ungrouped. */
fun groupOf(tool: EditorTool): ToolGroup? = TOOL_GROUPS.firstOrNull { tool in it.tools }

/** The id of the Shapes slot's group — the submenu the Figures settings section reorders. */
const val SHAPE_GROUP_ID = "shape"

/** The group behind the Shapes slot. */
val SHAPE_GROUP: ToolGroup = TOOL_GROUPS.first { it.id == SHAPE_GROUP_ID }

/**
 * The Shapes submenu's members in the user's [order]: the names it lists first, then any member it
 * omits appended in factory order, so a figure added in a later release still appears rather than
 * vanishing for an existing install.
 */
fun orderedShapeTools(order: List<String>): List<EditorTool> {
    val all = SHAPE_GROUP.tools
    val byName = all.associateBy { it.name }
    val listed = order.distinct().mapNotNull { byName[it] }
    return listed + all.filterNot { it in listed }
}

/** [orderedShapeTools] minus the members the user has hidden. */
fun visibleShapeTools(order: List<String>, hidden: Set<String>): List<EditorTool> =
    orderedShapeTools(order).filterNot { it.name in hidden }

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
        val group = TOOL_GROUPS.firstOrNull { it.id == id.trim() } ?: return@mapNotNull null
        val tool = runCatching { enumValueOf<EditorTool>(name.trim()) }.getOrNull() ?: return@mapNotNull null
        if (tool in group.tools) group.id to tool else null
    }.toMap()
}
