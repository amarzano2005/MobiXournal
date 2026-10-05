package com.mobixournal.ui

/**
 * Items available for the secondary (top) toolbar.
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
    RailItem("square", "Square"),
    RailItem("rhombus", "Rhombus"),
    RailItem("trapezoid", "Trapezoid"),
    RailItem("pentagon", "Pentagon"),
    RailItem("hexagon", "Hexagon"),
    RailItem("spline", "Spline"),
    RailItem("axis", "Coordinate axis"),
    // Multi-tool dropdown groups & tools
    RailItem("arrow", "Arrows"),
    RailItem("table", "Table"),
    RailItem("circuit", "Physical circuits"),
    RailItem("logic", "Logic gates"),
    // Popups
    RailItem("guides", "Drawing guides"),
)

/**
 * Default order of buttons in the secondary top bar: line, rectangle, individual shapes,
 * arrows (dropdown), table, physical circuits, logic gates, and drawing guides.
 */
val DEFAULT_TOP_BAR_ORDER: List<String> = listOf(
    "line", "rectangle", "ellipse", "triangle", "square", "rhombus",
    "trapezoid", "pentagon", "hexagon", "spline", "axis", "arrow",
    "table", "circuit", "logic", "guides",
)

/** The top bar positions in the user's [order], followed by any unlisted items in factory order. */
fun orderedTopBarItems(order: List<String>): List<RailItem> {
    val byId = TOP_BAR_ITEMS.associateBy { it.id }
    val listed = order.distinct().mapNotNull { byId[it] }
    return listed + TOP_BAR_ITEMS.filterNot { it in listed }
}

/** [orderedTopBarItems] minus items in [hidden]. */
fun visibleTopBarItems(order: List<String>, hidden: Set<String>): List<RailItem> =
    orderedTopBarItems(order).filterNot { it.id in hidden }

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
    "ellipse" -> EditorTool.ELLIPSE
    "square" -> EditorTool.SQUARE
    "rhombus" -> EditorTool.RHOMBUS
    "pentagon" -> EditorTool.PENTAGON
    "hexagon" -> EditorTool.HEXAGON
    "axis" -> EditorTool.COORDINATE_AXIS
    "spline" -> EditorTool.SPLINE
    "table" -> EditorTool.TABLE
    else -> null
}
