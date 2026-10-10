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
    RailItem("square", "Square"),
    RailItem("ellipse", "Circle / Ellipse"),
    RailItem("triangle", "Triangle"),
    RailItem("rhombus", "Rhombus"),
    RailItem("trapezoid", "Trapezoid"),
    RailItem("pentagon", "Pentagon"),
    RailItem("hexagon", "Hexagon"),
    RailItem("spline", "Spline"),
    // Multi-tool dropdown groups & tools
    RailItem("arrow", "Arrows"),
    RailItem("axis", "Coordinate axis"),
    RailItem("table", "Table"),
    RailItem("circuit", "Passive circuits"),
    RailItem("circuit_active", "Active circuits"),
    RailItem("logic", "Logic gates"),
    // STEM + popups
    RailItem("graph", "Function graph"),
    RailItem("guides", "Drawing guides"),
)

/**
 * Default order of buttons in the Secondary Toolbar: line, rectangle, square, individual shapes,
 * arrows (dropdown), the Cartesian axis, table, passive circuits, active circuits, logic gates, the
 * function graph, and drawing guides. The arrows dropdown sits ahead of the single Cartesian-axis
 * figure so the multi-member slot stays with the other dropdown groups, and the axis closes the run
 * of individual figures. The graph sits second from the end — one tap from the tools' end of the row,
 * but before the guides pop-up, which stays the row's last slot.
 */
val DEFAULT_TOP_BAR_ORDER: List<String> = listOf(
    "line", "rectangle", "square", "ellipse", "triangle", "rhombus",
    "trapezoid", "pentagon", "hexagon", "spline", "arrow", "axis",
    "table", "circuit", "circuit_active", "logic", "graph", "guides",
)

/**
 * The Secondary Toolbar positions in the user's [order], with any item the order doesn't name — one
 * added since that order was written — dropped in at its **factory position** rather than tacked onto
 * the end.
 *
 * Simply appending unlisted items would put a brand-new slot after everything the user has, which is
 * not where it was designed to sit: the function-graph button belongs next to the guides pop-up, not
 * past it. Each unknown item therefore goes immediately ahead of the next factory item the saved order
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
    EditorTool.SQUARE to "square",
    EditorTool.ELLIPSE to "ellipse",
    EditorTool.TRIANGLE to "triangle",
    EditorTool.RHOMBUS to "rhombus",
    EditorTool.TRAPEZOID to "trapezoid",
    EditorTool.PENTAGON to "pentagon",
    EditorTool.HEXAGON to "hexagon",
    EditorTool.SPLINE to "spline",
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
 * through a plain single-tool button would hide that menu.
 */
fun singleToolForTopBarId(id: String): EditorTool? = when (id) {
    "line" -> EditorTool.LINE
    "rectangle" -> EditorTool.RECTANGLE
    "square" -> EditorTool.SQUARE
    "ellipse" -> EditorTool.ELLIPSE
    "rhombus" -> EditorTool.RHOMBUS
    "pentagon" -> EditorTool.PENTAGON
    "hexagon" -> EditorTool.HEXAGON
    "axis" -> EditorTool.COORDINATE_AXIS
    "spline" -> EditorTool.SPLINE
    "table" -> EditorTool.TABLE
    else -> null
}
