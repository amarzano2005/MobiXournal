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
 * 2D figures | arrows & axes | function graph & table | circuits & logic | drawing guides.
 */
internal val FACTORY_TOP_BAR_ORDER_WITH_DIVIDERS: List<String> = listOf(
    "line", "rectangle", "ellipse", "triangle", "trapezoid", "polygons",
    "div_1",
    "arrow", "axis",
    "div_2",
    "graph", "table",
    "div_3",
    "circuit", "circuit_active", "logic",
    "div_4",
    "guides",
)

private val PREVIOUS_THREE_DIVIDER_ORDER: List<String> = listOf(
    "line", "rectangle", "ellipse", "triangle", "trapezoid", "polygons", "arrow",
    "div_1",
    "axis", "graph", "table",
    "div_2",
    "circuit", "circuit_active", "logic",
    "div_3",
    "guides",
)

/** Upgrade an untouched factory top-bar order (0-divider or 3-divider) to [FACTORY_TOP_BAR_ORDER_WITH_DIVIDERS]. */
internal fun upgradeFactoryTopBarDividers(order: List<String>): List<String> =
    if (order == DEFAULT_TOP_BAR_ORDER || order == PREVIOUS_THREE_DIVIDER_ORDER) {
        FACTORY_TOP_BAR_ORDER_WITH_DIVIDERS
    } else {
        order
    }

internal fun loadTopBarOrder(
    prefs: android.content.SharedPreferences,
    defaultOrder: List<String>,
    orderKey: String,
    migratedKey: String,
    seededKey: String,
): List<String> {
    val saved = decodeTopBarIds(prefs.getString(orderKey, null))
    if (saved.isEmpty()) return defaultOrder
    val migratedDone = prefs.getBoolean(migratedKey, false)
    val seededDone = prefs.getBoolean(seededKey, false)
    val migrated = if (migratedDone) saved else migrateTopBarOrder(saved)
    val seeded = if (seededDone) migrated else upgradeFactoryTopBarDividers(migrated)
    if (!migratedDone || !seededDone) {
        prefs.edit()
            .putBoolean(migratedKey, true)
            .putBoolean(seededKey, true)
            .putString(orderKey, encodeTopBarIds(seeded))
            .apply()
    }
    return seeded
}
