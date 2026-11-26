package com.mobixournal.ui

internal const val DIVIDER_ID_PREFIX = "div_"

internal fun isDividerId(id: String): Boolean = id.startsWith(DIVIDER_ID_PREFIX)

internal fun dividerRailItem(id: String): RailItem = RailItem(id, "Divider")

/** Smallest unused `"div_n"` divider identifier (`n >= 1`) not present in [existingIds]. */
internal fun nextDividerId(existingIds: Collection<String>): String {
    var index = 1
    while ("$DIVIDER_ID_PREFIX$index" in existingIds) index++
    return "$DIVIDER_ID_PREFIX$index"
}

/**
 * Strips leading, trailing, and consecutive divider items so a live toolbar never shows an orphan
 * or doubled hairline when adjacent tools are hidden.
 */
internal fun cleanVisibleDividers(items: List<RailItem>): List<RailItem> {
    val out = ArrayList<RailItem>(items.size)
    for (item in items) {
        if (isDividerId(item.id)) {
            if (out.isEmpty() || isDividerId(out.last().id)) continue
            out.add(item)
        } else {
            out.add(item)
        }
    }
    if (out.isNotEmpty() && isDividerId(out.last().id)) {
        out.removeAt(out.lastIndex)
    }
    return out
}

/**
 * Default Secondary Toolbar order with family dividers between:
 * 2D figures & arrows | axes, graphs & table | circuits & logic | drawing guides.
 */
internal val FACTORY_TOP_BAR_ORDER_WITH_DIVIDERS: List<String> = listOf(
    "line", "rectangle", "ellipse", "triangle", "trapezoid", "polygons", "arrow",
    "div_1",
    "axis", "graph", "table",
    "div_2",
    "circuit", "circuit_active", "logic",
    "div_3",
    "guides",
)
