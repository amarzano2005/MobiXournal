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
    RailItem("layers", "Layers"),
    RailItem("pages", "Pages"),
)

/**
 * Every rail position in factory order, by id — the order the Toolbar settings screen lists them in
 * and the order the rail draws them. The drawing tools reached for most sit at the top (pen,
 * highlighter, colour & size, eraser), then the rest of the tools, then the popup panels.
 *
 * The rail is **writing tools only**: audio, play object, shape recognition, drawing guides, the sheet
 * background and every STEM slot that duplicates the Secondary Toolbar are deliberately absent, so the
 * settings list cannot offer them back (see [TOOL_GROUPS] and [TOP_BAR_ITEMS]).
 *
 * The **Zoom** position is retired: pinch is how a page is zoomed, the canvas badge already shows the
 * factor and taps back to 100%, so a permanent rail button that only reopened a percentage read-out
 * cost a slot. A saved order naming `zoom` simply drops it, as with every other retired id.
 *
 * The **Background** position is retired: paper style, ruling spacing and stationery are the same
 * errand as the page's own size and navigation, so the **Pages** menu carries one row that opens
 * [PageBackgroundDialog] (see [ToolbarPagesCallbacks]) rather than a rail slot of its own. A saved
 * rail order that names `background` simply drops it, as with every other retired id.
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
    "text", "insert", "vspace", "pages", "layers",
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

/**
 * The positions the rail **always** keeps, in this order, under a hairline rule at its foot.
 *
 * Pages and Layers read the document rather than choose a tool, so they are navigation and not part of
 * the tool order: they are the last two slots of the rail whether the user reordered anything or not,
 * and the Toolbar settings section neither lists them for reordering nor offers a switch to hide them.
 * A rail whose navigation could be scrolled away, hidden or shuffled into the pens lost the two doors a
 * reader reaches for most.
 */
val PINNED_RAIL_IDS: List<String> = listOf("pages", "layers")

/** The tool group behind a rail item, or null when the item is a popup panel. */
fun toolGroupForRailItem(id: String): ToolGroup? = TOOL_GROUPS.firstOrNull { it.id == id }

/**
 * The rail's positions in the user's [order]: known ids first in the order given, then any item the
 * saved order doesn't mention, appended in factory order. Appending rather than dropping is what
 * makes a rail item added in a later release still show up for an existing install.
 */
fun orderedRailItems(order: List<String>): List<RailItem> {
    val byId = RAIL_ITEMS.associateBy { it.id }
    val listed = order.distinct().mapNotNull { byId[it] }.filterNot { it.id in PINNED_RAIL_IDS }
    val reorderable = listed + RAIL_ITEMS.filterNot { it in listed || it.id in PINNED_RAIL_IDS }
    return reorderable + PINNED_RAIL_IDS.mapNotNull { byId[it] }
}

/**
 * [orderedRailItems] minus the positions the user has hidden — except the [PINNED_RAIL_IDS], which a
 * saved `railHidden` from an older build may still name and which the rail keeps regardless.
 */
fun visibleRailItems(order: List<String>, hidden: Set<String>): List<RailItem> =
    orderedRailItems(order).filterNot { it.id in hidden && it.id !in PINNED_RAIL_IDS }

/** One stretch of the rail and whether a rule opens it: the tools, then the pinned panels under a line. */
data class RailSection(val items: List<RailItem>, val ruled: Boolean)

/**
 * The rail cut into the two stretches it draws: everything the user orders, then the pinned panels.
 *
 * A pure cut rather than a `when` inside the rail, so the rule is a value the tests can read: the
 * separator belongs to the section, not to a slot, and a pinned position can never be drawn among the
 * tools with no line between them.
 */
fun railSections(items: List<RailItem>): List<RailSection> {
    val pinned = items.filter { it.id in PINNED_RAIL_IDS }
    val tools = items.filterNot { it.id in PINNED_RAIL_IDS }
    return listOfNotNull(
        tools.takeIf { it.isNotEmpty() }?.let { RailSection(it, ruled = false) },
        pinned.takeIf { it.isNotEmpty() }?.let { RailSection(it, ruled = true) },
    )
}

/**
 * [order] with the item at [index] moved by [delta] positions. Out-of-range moves are no-ops, and
 * the result is always a full explicit order so the next move has something stable to permute.
 */
fun moveRailItem(order: List<String>, index: Int, delta: Int): List<String> {
    val all = orderedRailItems(order).map { it.id }
    // The pinned tail is not part of the permutation: an index that lands on it is a no-op, and the
    // result always puts Pages and Layers back at the foot of the rail.
    val ids = all.filterNot { it in PINNED_RAIL_IDS }.toMutableList()
    val to = index + delta
    if (index in ids.indices && to in ids.indices) ids.add(to, ids.removeAt(index))
    return ids + all.filter { it in PINNED_RAIL_IDS }
}

/** Encode an id list for SharedPreferences: comma-separated. */
fun encodeRailIds(ids: Collection<String>): String = ids.joinToString(",")

/** Parse what [encodeRailIds] wrote, dropping ids that no longer name a rail position. */
fun decodeRailIds(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val known = RAIL_ITEMS.map { it.id }.toSet()
    return raw.split(',').map { it.trim() }.filter { it in known }.distinct()
}
