package com.mobixournal.desktop

/**
 * The **values** the window's Settings offer, as types rather than strings.
 *
 * They live here, apart from the store that persists them (`DesktopSettings`) and the surface that shows
 * them (`SettingsSurface`), because three places have to agree on them: the store's decoder, the surface's
 * rows and the chrome that obeys the answer. A setting whose values were spelled out at each of those
 * three places would be three chances to disagree — and the wrong spelling is silent, because a property
 * file answers nothing for a name no enum knows.
 *
 * Every enum carries its own **label** (the word on the row) and an `of` reader that falls back to the
 * default rather than throwing: a hand-edited `desktop.properties` must open a window, not stop one.
 */

/**
 * **Where the tool strips stand.**
 *
 * The tablet has one shape: the Main Toolbar is a vertical rail down the edge opposite the writing hand,
 * and the figures live in the top bar. A window has room for the rail, for a horizontal ribbon under the
 * header, or for both — so the choice is the tablet's own placement plus the ribbon the window can afford,
 * and it is a **setting**, never a control in the chrome it moves (`docs/desktop-workstation.md`).
 */
enum class StripPlacement(val label: String) {
    /** The vertical rail down [RailSide], which is the tablet's Main Toolbar. */
    SIDE("Side"),

    /** The horizontal ribbon under the header, and no rail. */
    TOP("Top"),

    /** Both lanes: the ribbon under the header and the rail beside the canvas. */
    BOTH("Both"),
    ;

    /** The rail's lane is drawn (Side and Both). */
    val showsRail: Boolean get() = this != TOP

    /** The ribbon's band is drawn (Top and Both). */
    val showsRibbon: Boolean get() = this != SIDE

    companion object {
        /** The placement a window with no setting gets: the tablet's own rail. */
        val DEFAULT: StripPlacement = SIDE

        /**
         * [name] as a placement, reading **both** the current names and the three this file used while
         * the choice lived only in the properties file (`Rail`, `Ribbon`, `Both`), so an install that has
         * already picked one keeps it.
         */
        fun of(name: String?): StripPlacement = when (name?.trim()?.lowercase()) {
            "top", "ribbon" -> TOP
            "both" -> BOTH
            // "side", "rail", absent or unknown: the rail, which is what the window always had.
            else -> SIDE
        }
    }
}

/**
 * **Which edge the rail takes** — the tablet's handedness answer.
 *
 * The phone's Toolbar section calls this *Position* and explains it the same way: the rail goes on the
 * side **opposite the writing hand**, so a right hand gets Left (the default) and a left hand Right. The
 * page list and the inspector keep their own sides, so moving the rail never moves them.
 */
enum class RailSide(val label: String) {
    /** The edge a **right** hand writes from. The default. */
    LEFT("Left"),

    /** The edge a **left** hand writes from. */
    RIGHT("Right"),
    ;

    companion object {
        val DEFAULT: RailSide = LEFT

        fun of(name: String?): RailSide =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * **How a document opens**: the zoom the window picks for it before the reader touches anything.
 *
 * `AUTO` is the window's own rule ([FitMode.AUTO] resolves a tall page to fit-width and the rest to
 * fit-page), which is why it leads: a spread of two pages and a single sheet do not want the same answer.
 */
enum class FitOnOpen(val label: String, val fit: FitMode) {
    AUTO("Automatic", FitMode.AUTO),
    PAGE("Fit page", FitMode.PAGE),
    WIDTH("Fit width", FitMode.WIDTH),
    FREE("Free zoom", FitMode.NONE),
    ;

    companion object {
        val DEFAULT: FitOnOpen = AUTO

        fun of(name: String?): FitOnOpen =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * **When the page list shows itself.**
 *
 * `AUTOMATIC` is the rule the window shipped with — more than one page is the point at which the list
 * stops being a picture of the sheet in front of you (`pageListWorthShowing`) — and the other two answers
 * are for a reader who knows their own habit.
 */
enum class SidebarPolicy(val label: String) {
    AUTOMATIC("Automatic"),
    ALWAYS("Always"),
    NEVER("Never"),
    ;

    companion object {
        val DEFAULT: SidebarPolicy = AUTOMATIC

        fun of(name: String?): SidebarPolicy =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * **Whether the inspector lane may open.**
 *
 * The lane has no empty state on purpose: it is the editor of the element the reader is holding, and a
 * column of blank controls beside a page is furniture (`InspectorLane` returns early when there is nothing
 * to edit). So the choice is whether it may appear at all — never a way to pin an empty one.
 */
enum class InspectorPolicy(val label: String) {
    /** Shown while something is being placed, typed or edited — the window's own rule. */
    AUTOMATIC("When I can edit something"),

    /** Never shown; the same edits are still reachable from the selection bar. */
    NEVER("Never"),
    ;

    companion object {
        val DEFAULT: InspectorPolicy = AUTOMATIC

        fun of(name: String?): InspectorPolicy =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * **Which pen stream the window reads** — `docs/tablet.md`'s two readers, in the order the window tries
 * them.
 *
 * `AUTOMATIC` is what the window has always done: Wintab first, because the driver's packets carry the
 * pressure at the tablet's own rate, then Windows Ink for a driver that only reports pointer events.
 * The other two exist for the case where both work and only one is right for the pen in hand, and the
 * status bar names whichever one is actually open, so a preference that cannot be honoured says so.
 */
enum class PenStream(val label: String) {
    AUTOMATIC("Automatic"),
    WINTAB("Wintab"),
    INK("Windows Ink"),
    ;

    companion object {
        val DEFAULT: PenStream = AUTOMATIC

        fun of(name: String?): PenStream =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
