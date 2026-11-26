package com.mobixournal.ui

/** Width step per ungrouped top-bar tool button (32 dp button + 4 dp gap + breathing room). */
internal const val TOP_BAR_SLOT_STEP_DP = 40f

/** Horizontal padding reserved by the Secondary Toolbar dock capsule. */
internal const val TOP_BAR_DOCK_PAD_DP = 12f

/**
 * True when [availableWidthDp] is too narrow to fit [itemCount] separate Secondary Toolbar icons
 * without horizontal scrolling, triggering adaptive family grouping on compact phones.
 */
internal fun shouldCollapseTopBar(availableWidthDp: Float, itemCount: Int): Boolean =
    itemCount > 0 && availableWidthDp > 0f &&
        availableWidthDp < itemCount * TOP_BAR_SLOT_STEP_DP + TOP_BAR_DOCK_PAD_DP

/**
 * One entry in the Secondary Toolbar dock: either a single [RailItem] slot (kept as-is) or a
 * synthesized [ToolGroup] combining related families when horizontal space is tight.
 */
internal sealed interface CompactTopBarEntry {
    data class Single(val item: RailItem) : CompactTopBarEntry
    data class Group(val group: ToolGroup) : CompactTopBarEntry
}

private val SHAPE_ID_TOOLS: List<Pair<String, EditorTool>> = listOf(
    "rectangle" to EditorTool.RECTANGLE,
    "ellipse" to EditorTool.ELLIPSE,
    "triangle" to EditorTool.TRIANGLE,
    "trapezoid" to EditorTool.TRAPEZOID,
    "polygons" to EditorTool.POLYGON,
)

private val KNOWN_GROUPED_IDS: Set<String> = setOf(
    "line", "arrow", "rectangle", "ellipse", "triangle", "trapezoid", "polygons",
    "axis", "graph", "table", "circuit", "circuit_active", "logic", "guides",
)

/**
 * Collapses the visible Secondary Toolbar [items] into up to 7 family entries for narrow screens,
 * preserving user visibility rules (`topBarHidden` / `shapeHidden`). A family reduced to one
 * visible item degrades to [CompactTopBarEntry.Single] so its native picker/tap behaviour stays intact.
 */
internal fun compactTopBarEntries(items: List<RailItem>): List<CompactTopBarEntry> {
    if (items.isEmpty()) return emptyList()
    val byId = items.associateBy { it.id }
    val out = ArrayList<CompactTopBarEntry>()

    addLinesEntry(byId, out)
    addShapesEntry(byId, out)
    addMathEntry(byId, out)
    byId["table"]?.let { out += CompactTopBarEntry.Single(it) }
    addCircuitsEntry(byId, out)
    byId["logic"]?.let { out += CompactTopBarEntry.Single(it) }
    byId["guides"]?.let { out += CompactTopBarEntry.Single(it) }

    for (item in items) {
        if (item.id !in KNOWN_GROUPED_IDS) out += CompactTopBarEntry.Single(item)
    }
    return out
}

private fun addLinesEntry(byId: Map<String, RailItem>, out: MutableList<CompactTopBarEntry>) {
    val lineItem = byId["line"]
    val arrowItem = byId["arrow"]
    if (lineItem == null && arrowItem == null) return
    if (lineItem != null && arrowItem == null) {
        out += CompactTopBarEntry.Single(lineItem)
        return
    }
    if (lineItem == null && arrowItem != null) {
        out += CompactTopBarEntry.Single(arrowItem)
        return
    }
    val tools = listOf(EditorTool.LINE, EditorTool.ARROW, EditorTool.DOUBLE_ARROW, EditorTool.DIMENSION)
    out += CompactTopBarEntry.Group(
        ToolGroup(
            id = "compact_lines",
            label = "Lines & Arrows",
            tools = tools,
            pickerLayout = listOf(
                listOf(EditorTool.LINE, EditorTool.ARROW),
                listOf(EditorTool.DOUBLE_ARROW, EditorTool.DIMENSION),
            ),
        ),
    )
}

private fun addShapesEntry(byId: Map<String, RailItem>, out: MutableList<CompactTopBarEntry>) {
    val present = SHAPE_ID_TOOLS.filter { (id, _) -> id in byId }
    if (present.isEmpty()) return
    if (present.size == 1) {
        out += CompactTopBarEntry.Single(byId.getValue(present.first().first))
        return
    }
    val tools = present.map { it.second }
    out += CompactTopBarEntry.Group(
        ToolGroup(
            id = "compact_shapes",
            label = "Shapes",
            tools = tools,
            pickerLayout = tools.chunked(3),
        ),
    )
}

private fun addMathEntry(byId: Map<String, RailItem>, out: MutableList<CompactTopBarEntry>) {
    val axis = byId["axis"]
    val graph = byId["graph"]
    if (axis == null && graph == null) return
    if (axis != null && graph == null) {
        out += CompactTopBarEntry.Single(axis)
        return
    }
    if (axis == null && graph != null) {
        out += CompactTopBarEntry.Single(graph)
        return
    }
    val tools = listOf(EditorTool.COORDINATE_AXIS, EditorTool.PLOT)
    out += CompactTopBarEntry.Group(
        ToolGroup(
            id = "compact_math",
            label = "Axes & Graphs",
            tools = tools,
            pickerLayout = listOf(tools),
        ),
    )
}

private fun addCircuitsEntry(byId: Map<String, RailItem>, out: MutableList<CompactTopBarEntry>) {
    val passive = byId["circuit"]?.let { secondaryToolGroup("circuit") }
    val active = byId["circuit_active"]?.let { secondaryToolGroup("circuit_active") }
    if (passive == null && active == null) return
    if (passive != null && active == null) {
        out += CompactTopBarEntry.Single(byId.getValue("circuit"))
        return
    }
    if (passive == null && active != null) {
        out += CompactTopBarEntry.Single(byId.getValue("circuit_active"))
        return
    }
    val p = requireNotNull(passive)
    val a = requireNotNull(active)
    out += CompactTopBarEntry.Group(
        ToolGroup(
            id = "compact_circuits",
            label = "Circuits",
            tools = p.tools + a.tools,
            pickerLayout = p.pickerLayout + a.pickerLayout,
        ),
    )
}
