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
 * **Where the tool strips stand**: three answers, and each one is a side of the window.
 *
 * The tablet has one shape — the Main Toolbar is a vertical rail down the edge opposite the writing hand —
 * and the window offers that rail on either edge, or the same tools as a ribbon under the header. The
 * section used to ask two questions (a placement of Side/Top/Both and then which side the rail took),
 * which meant four answers for three lanes and a "both" nobody asked for; the placement is now one row
 * of three, and the edge is part of the answer.
 *
 * It is a **setting**, never a control in the chrome it moves (`docs/desktop-workstation.md`).
 */
enum class StripPlacement(val label: String) {
    /** The vertical rail down the left edge, the tablet's own home for a right hand. */
    LEFT("Left"),

    /** The vertical rail down the right edge, for a left hand. */
    RIGHT("Right"),

    /** The horizontal ribbon under the header, and no rail. */
    TOP("Top"),
    ;

    /** The rail's lane is drawn. */
    val showsRail: Boolean get() = this != TOP

    /** The ribbon's band is drawn — the top placement, and only it. */
    val showsRibbon: Boolean get() = this == TOP

    /** The edge the rail takes, or null when the strips are a ribbon. */
    val railSide: RailSide?
        get() = when (this) {
            LEFT -> RailSide.LEFT
            RIGHT -> RailSide.RIGHT
            TOP -> null
        }

    companion object {
        /** The placement a window with no setting gets: the tablet's own rail, on the left. */
        val DEFAULT: StripPlacement = LEFT

        /**
         * [name] as a placement, reading the current names **and** the legacy spellings this file and the
         * properties store used (`Side`, `Rail`, `Ribbon`, `Both`) plus the old separate rail side, so an
         * install that already picked an edge keeps it instead of jumping to the left.
         */
        fun of(name: String?, legacyRailSide: String? = null): StripPlacement {
            val rail = if (RailSide.of(legacyRailSide) == RailSide.RIGHT) RIGHT else LEFT
            return when (name?.trim()?.lowercase()) {
                "left" -> LEFT
                "right" -> RIGHT
                "top", "ribbon", "both" -> TOP
                // "side", "rail", absent or unknown: the rail, on the edge the old row stored.
                else -> rail
            }
        }
    }
}

/**
 * **Which edge the rail takes** — the tablet's handedness answer, as [StripPlacement] spells it.
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
 * **What a new document is written as** — the tablet's three answers, on the window's *New* command.
 *
 * The choice is a choice of *document*, not of file: `.mobix` keeps a figure, a table or a component as the
 * recipe it was authored as, `.xopp` is the file desktop Xournal++ reads everywhere, and the zipped package
 * is the same `.xopp` with its attachments inside one file instead of beside it. A window used to write
 * every new page as a gzip `.xopp` and leave the container to Save As, which meant the laptop and the
 * tablet disagreed about what New makes.
 *
 * [container] is what the new tab holds (and therefore what its first save writes); [extension] is the name
 * the tab is born with.
 */
enum class DesktopNewDocumentFormat(
    val label: String,
    val extension: String,
    /** One line saying what choosing this container means, for the Save As question. */
    val summary: String,
) {
    MOBIX(
        "MobiXournal (.mobix)",
        ".mobix",
        "Figures, tables and components stay live, with the strokes' telemetry.",
    ),
    XOPP(
        "Xournal++ (.xopp)",
        ".xopp",
        "The plain gzip document desktop Xournal++ reads everywhere.",
    ),
    ZIPPED(
        "Xournal++ package (.xopp, zipped)",
        ".xopp",
        "The same document as a .zip, with its pictures and PDFs inside it.",
    ),
    ;

    /** The container a tab opened on this answer carries until Save As names another. */
    val container: DesktopContainer
        get() = when (this) {
            MOBIX -> DesktopContainer.MOBIX
            XOPP -> DesktopContainer.XOPP_GZIP
            ZIPPED -> DesktopContainer.XOPP_ZIP
        }

    companion object {
        /** What the window has always written, so an untouched install keeps its habit. */
        val DEFAULT: DesktopNewDocumentFormat = XOPP

        fun of(name: String?): DesktopNewDocumentFormat =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: DEFAULT

        /** The answer that writes a document opened as [container] again — the Save As dialog's default. */
        fun ofContainer(container: DesktopContainer): DesktopNewDocumentFormat = when (container) {
            DesktopContainer.MOBIX -> MOBIX
            DesktopContainer.XOPP_GZIP -> XOPP
            DesktopContainer.XOPP_ZIP -> ZIPPED
        }
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
