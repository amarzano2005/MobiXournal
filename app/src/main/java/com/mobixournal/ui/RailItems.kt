package com.mobixournal.ui

/**
 * One button position on the tool rail — either a tool slot (a [ToolGroup]) or one of the popup
 * panels (colour, size, …). The Toolbar settings section reorders and hides these; [SideToolbar]
 * renders whatever [visibleRailItems] returns, in that order.
 */
data class RailItem(
    /** Stable key — the SharedPreferences identity of this position. Never rename. */
    val id: String,
    /** Human-readable name, shown in the Toolbar settings list. */
    val label: String,
)

/**
 * The popup-panel positions, in factory order. Tool slots come from [TOOL_GROUPS].
 *
 * `favorites` is the odd one out: its main surface is not a button that opens something, it *is* the
 * control — the three favourite colours of the tool in play stacked one tap away, with the chevron
 * beside them onto the full pop-up ([ColorSizeRailSlot]).
 *
 * Its **id stays `favorites`** because the id is the SharedPreferences identity of the position and is
 * never renamed; its *label* reads **Colour & size** because that is now the whole errand the slot
 * covers. The separate `color` position the two used to occupy is retired — two rail slots for one job
 * cost a button each and made the rail taller than the screen.
 */
val PANEL_RAIL_ITEMS: List<RailItem> = listOf(
    RailItem("favorites", "Colour & size"),
    RailItem("shapes", "Shape recognition"),
    RailItem("guides", "Guides"),
    RailItem("layers", "Layers"),
    RailItem("zoom", "Zoom"),
    RailItem("background", "Background"),
    RailItem("pages", "Pages"),
    RailItem("audio", "Audio"),
)

/**
 * Every rail position in factory order, by id — the order the Toolbar settings screen lists them in
 * and the order the rail draws them. The drawing tools reached for most sit at the top (pen,
 * highlighter, colour & size, eraser), then the rest of the tools, then the popup panels, with the
 * rarer controls (shape recognition, guides) trailing the rail.
 *
 * **Colour & size** (id `favorites`) sits directly under **Highlighter** and covers the whole errand:
 * the three colours you keep coming back to as a stack of dots, and the chevron beside them onto every
 * colour there is plus the tip size and the line style. There is no separate colour slot any more, and
 * no **Style** slot either — line style and fill live in that same pop-up, since they are set together
 * with the colour and the width.
 *
 * This is the default an install starts from; the user can reorder and hide rows in the Toolbar
 * section (persisted in `AppSettings.railOrder` / `railHidden`).
 */
private val FACTORY_RAIL_ORDER: List<String> = listOf(
    "pen", "highlighter", "favorites", "eraser", "pan", "select",
    "text", "insert", "vspace", "zoom", "pages", "background", "layers", "audio", "play", "shapes",
    "line", "rectangle", "shape", "arrow", "table", "circuit", "circuit_active", "logic", "guides",
)

/**
 * Every rail position in factory order: the ids [FACTORY_RAIL_ORDER] names, in that order, then any
 * position it does not mention appended afterwards in natural order. Appending rather than dropping
 * is what makes a tool or panel added in a later release still show up instead of vanishing.
 */
val RAIL_ITEMS: List<RailItem> = run {
    val all = TOOL_GROUPS.map { RailItem(it.id, it.label) } + PANEL_RAIL_ITEMS
    val byId = all.associateBy { it.id }
    FACTORY_RAIL_ORDER.mapNotNull { byId[it] } + all.filterNot { it.id in FACTORY_RAIL_ORDER }
}

/** The tool group behind a rail item, or null when the item is a popup panel. */
fun toolGroupForRailItem(id: String): ToolGroup? = TOOL_GROUPS.firstOrNull { it.id == id }

/**
 * The rail's positions in the user's [order]: known ids first in the order given, then any item the
 * saved order doesn't mention, appended in factory order. Appending rather than dropping is what
 * makes a rail item added in a later release still show up for an existing install.
 */
fun orderedRailItems(order: List<String>): List<RailItem> {
    val byId = RAIL_ITEMS.associateBy { it.id }
    val listed = order.distinct().mapNotNull { byId[it] }
    return listed + RAIL_ITEMS.filterNot { it in listed }
}

/** [orderedRailItems] minus the positions the user has hidden. */
fun visibleRailItems(order: List<String>, hidden: Set<String>): List<RailItem> =
    orderedRailItems(order).filterNot { it.id in hidden }

/**
 * [order] with the item at [index] moved by [delta] positions. Out-of-range moves are no-ops, and
 * the result is always a full explicit order so the next move has something stable to permute.
 */
fun moveRailItem(order: List<String>, index: Int, delta: Int): List<String> {
    val ids = orderedRailItems(order).map { it.id }.toMutableList()
    val to = index + delta
    if (index !in ids.indices || to !in ids.indices) return ids
    ids.add(to, ids.removeAt(index))
    return ids
}

/** Encode an id list for SharedPreferences: comma-separated. */
fun encodeRailIds(ids: Collection<String>): String = ids.joinToString(",")

/** Parse what [encodeRailIds] wrote, dropping ids that no longer name a rail position. */
fun decodeRailIds(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val known = RAIL_ITEMS.map { it.id }.toSet()
    return raw.split(',').map { it.trim() }.filter { it in known }.distinct()
}
