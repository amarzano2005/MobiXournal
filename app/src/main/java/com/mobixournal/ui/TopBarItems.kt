package com.mobixournal.ui

/**
 * Items available for the Secondary Toolbar (top bar).
 *
 * Geometric figures are exposed individually so users can tap any shape with one touch,
 * while multi-member slots (arrows, triangles, physical circuits, logic gates, select, eraser) have
 * dropdown menus.
 */
val TOP_BAR_ITEMS: List<RailItem> = listOf(
    // Individual geometric shapes (one-by-one direct tools)
    RailItem("line", "Line"),
    RailItem("rectangle", "Rectangle"),
    RailItem("ellipse", "Circle / Ellipse"),
    RailItem("triangle", "Triangle"),
    RailItem("rhombus", "Rhombus"),
    RailItem("trapezoid", "Trapezoid"),
    RailItem("polygons", "Polygons"),
    // Multi-tool dropdown groups & tools
    RailItem("arrow", "Arrows"),
    RailItem("axis", "Coordinate axis"),
    RailItem("graph", "Function graph"),
    RailItem("table", "Table"),
    RailItem("circuit", "Passive circuits"),
    RailItem("circuit_active", "Active circuits"),
    RailItem("logic", "Logic gates"),
    // STEM + popups
    RailItem("guides", "Drawing guides"),
)

/**
 * Default order of buttons in the Secondary Toolbar: line, rectangle, the individual shapes, the
 * polygon slot (the generic polygon, then the pentagon and the hexagon), arrows (dropdown), the
 * Cartesian axis, the function graph,
 * table, passive circuits, active circuits, logic gates, and drawing guides. The function graph sits
 * immediately to the right of the Cartesian coordinate axis, keeping coordinate-based mathematical
 * graphing tools together.
 *
 * There is no square and no chemistry slot: a square is a rectangle, and the chemistry bonds and the
 * benzene ring left with the section that offered them.
 */
val DEFAULT_TOP_BAR_ORDER: List<String> = listOf(
    "line", "rectangle", "ellipse", "triangle", "rhombus",
    "trapezoid", "polygons", "arrow", "axis", "graph",
    "table", "circuit", "circuit_active", "logic", "guides",
)

/**
 * The Secondary Toolbar's **previous** factory orders — the arrow group *after* the Cartesian axis,
 * without the function graph, with it before guides, and with chemistry.
 *
 * They exist only so [migrateTopBarOrder] can tell a row the user never touched from one they
 * arranged. The whole settings object is written on any save, so a device whose row is still the
 * factory's carries a full copy of *that* factory list on disk: a factory reorder would never reach it,
 * because a saved order deliberately wins.
 */
private val LEGACY_TOP_BAR_ORDERS: List<List<String>> = listOf(
    // The row as it stood while the spline still had a slot of its own: that slot is gone now, and a
    // device that never arranged its row carries the whole old list on disk, so it needs this copy to
    // be recognised as "never touched" and follow the reorder.
    listOf(
        "line", "rectangle", "ellipse", "triangle", "rhombus",
        "trapezoid", "polygons", "spline", "arrow", "axis", "graph",
        "table", "circuit", "circuit_active", "logic", "guides",
    ),
    // The row as it stood with the square and the chemistry slot, which the current factory order
    // leaves out — a device whose row was never arranged still carries this copy on disk.
    listOf(
        "line", "rectangle", "square", "ellipse", "triangle", "rhombus",
        "trapezoid", "pentagon", "hexagon", "spline", "arrow", "axis", "graph",
        "table", "circuit", "circuit_active", "logic", "chemistry", "guides",
    ),
    listOf(
        "line", "rectangle", "square", "ellipse", "triangle", "rhombus",
        "trapezoid", "pentagon", "hexagon", "spline", "axis", "arrow",
        "table", "circuit", "circuit_active", "logic", "guides",
    ),
    listOf(
        "line", "rectangle", "square", "ellipse", "triangle", "rhombus",
        "trapezoid", "pentagon", "hexagon", "spline", "axis", "arrow",
        "table", "circuit", "circuit_active", "logic", "graph", "guides",
    ),
    listOf(
        "line", "rectangle", "square", "ellipse", "triangle", "rhombus",
        "trapezoid", "pentagon", "hexagon", "spline", "arrow", "axis",
        "table", "circuit", "circuit_active", "logic", "chemistry", "graph", "guides",
    ),
)

/**
 * [order] as the factory order when it is *still* an old factory order verbatim, otherwise untouched.
 *
 * A user-arranged row always differs from the old default — that is what arranging it means — so the
 * comparison is enough to distinguish "never touched" from "deliberately arranged this way", and a
 * hand-made row is never rewritten.
 */
fun migrateTopBarOrder(order: List<String>): List<String> =
    if (order in LEGACY_TOP_BAR_ORDERS) DEFAULT_TOP_BAR_ORDER else order

/**
 * The Secondary Toolbar positions in the user's [order], with any item the order doesn't name — one
 * added since that order was written — dropped in at its **factory position** rather than tacked onto
 * the end.
 *
 * Simply appending unlisted items would put a brand-new slot after everything the user has, which is
 * not where it was designed to sit: for instance, the function-graph button belongs next to the Cartesian
 * axis. Each unknown item therefore goes immediately ahead of the next factory item the saved order
 * *does* name, so an added slot arrives where it belongs in a row the user has already arranged.
 */
fun orderedTopBarItems(order: List<String>): List<RailItem> {
    val byId = TOP_BAR_ITEMS.associateBy { it.id }
    val listed = order.distinct().mapNotNull { byId[it] }
    if (listed.isEmpty()) return TOP_BAR_ITEMS
    val known = listed.mapTo(HashSet()) { it.id }
    val positioned = listed.toMutableList()
    for ((index, item) in TOP_BAR_ITEMS.withIndex()) {
        if (item.id in known) continue
        val nextKnown = TOP_BAR_ITEMS.drop(index + 1).firstOrNull { it.id in known }?.id
        val at = if (nextKnown == null) positioned.size else positioned.indexOfFirst { it.id == nextKnown }
        positioned.add(at, item)
    }
    return positioned
}

/**
 * Mapping between geometric figure tools and their Secondary Toolbar item ids.
 */
val FIGURE_TOOL_TO_TOP_BAR_ID: Map<EditorTool, String> = mapOf(
    EditorTool.LINE to "line",
    EditorTool.RECTANGLE to "rectangle",
    EditorTool.ELLIPSE to "ellipse",
    EditorTool.TRIANGLE to "triangle",
    EditorTool.RHOMBUS to "rhombus",
    EditorTool.TRAPEZOID to "trapezoid",
    EditorTool.COORDINATE_AXIS to "axis",
)

/**
 * Reverse mapping from Secondary Toolbar item id to EditorTool for geometric figures.
 */
val TOP_BAR_ID_TO_FIGURE_TOOL: Map<String, EditorTool> =
    FIGURE_TOOL_TO_TOP_BAR_ID.entries.associate { (k, v) -> v to k }

/**
 * All Secondary Toolbar ids that correspond to geometric figures.
 */
val ALL_FIGURE_TOP_BAR_IDS: Set<String> = FIGURE_TOOL_TO_TOP_BAR_ID.values.toSet()

/** [orderedTopBarItems] minus items in [hidden] or [shapeHidden]. */
fun visibleTopBarItems(
    order: List<String>,
    hidden: Set<String>,
    shapeHidden: Set<String> = emptySet(),
): List<RailItem> =
    orderedTopBarItems(order).filterNot { item ->
        if (item.id in hidden) return@filterNot true
        val tool = TOP_BAR_ID_TO_FIGURE_TOOL[item.id]
        if (tool != null && tool.name in shapeHidden) return@filterNot true
        false
    }

/** Move an item at [index] by [delta] positions. */
fun moveTopBarItem(order: List<String>, index: Int, delta: Int): List<String> {
    val ids = orderedTopBarItems(order).map { it.id }.toMutableList()
    val to = index + delta
    if (index !in ids.indices || to !in ids.indices) return ids
    ids.add(to, ids.removeAt(index))
    return ids
}

/** Encode an id list for SharedPreferences: comma-separated. */
fun encodeTopBarIds(ids: Collection<String>): String = ids.joinToString(",")

/** Parse what [encodeTopBarIds] wrote, dropping ids that no longer name a top bar position. */
fun decodeTopBarIds(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val known = TOP_BAR_ITEMS.map { it.id }.toSet()
    return raw.split(',').map { it.trim() }.filter { it in known }.distinct()
}

/**
 * Map top bar id to a single EditorTool if it represents an individual tool directly, or null
 * when the slot is rendered by a dedicated button instead. ["triangle"] and ["trapezoid"] return
 * null because they carry their own variant picker (isosceles / right / scalene); routing them
 * through a plain single-tool button would hide that menu. ["polygons"] is null for the same reason:
 * it is a **family** slot, resolved through [secondaryToolGroup], whose menu offers the generic
 * polygon, then the pentagon and the hexagon, in that order.
 */
fun singleToolForTopBarId(id: String): EditorTool? = when (id) {
    "line" -> EditorTool.LINE
    "rectangle" -> EditorTool.RECTANGLE
    "ellipse" -> EditorTool.ELLIPSE
    "rhombus" -> EditorTool.RHOMBUS
    "axis" -> EditorTool.COORDINATE_AXIS
    "table" -> EditorTool.TABLE
    else -> null
}
