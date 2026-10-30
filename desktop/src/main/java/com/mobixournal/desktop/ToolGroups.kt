package com.mobixournal.desktop

/**
 * A strip's **slot**, and the tools that share it — the tablet's tool-group idea, as the window uses it.
 *
 * On the tablet one slot stands for a family (the two erasers, the seven figures) and a chevron opens the
 * family's picker; the window drew every tool as a slot of its own, so the two strips shared their
 * material and their glyphs but not their structure. A group fixes that without a second tool list: the
 * members *are* [DesktopTool] entries, their order is the slot's order, and the shortcut table keeps
 * meaning the same tools — picking a member re-faces the slot and nothing else.
 *
 * **Only tools the canvas acts on are in a group.** The window used to offer eighteen slots and act on
 * twelve: Text, Plot, Table and Circuit were drawn but did nothing, and the selection tools were the same
 * promise. A slot that does nothing is worse than an absent one, so [DESKTOP_TOOL_GROUPS] holds
 * [DESKTOP_TOOLS_THAT_ACT] and no more; the tools still waiting are named with the task that brings each,
 * and a test fails if an acting tool is left out of a group or a group lists one that does nothing.
 */
class ToolGroup(val members: List<DesktopTool>) {

    init {
        require(members.isNotEmpty()) { "a group with no members cannot face a slot" }
    }

    /** The member the slot shows when nothing in the group is picked. */
    val face: DesktopTool get() = members.first()

    /** Whether the slot carries a chevron: a lone tool has nothing to pick from. */
    val hasPicker: Boolean get() = members.size > 1

    /**
     * What the slot shows for [selection]: the group's member that is picked, or the group's face.
     *
     * Deriving the face from the selection is what makes the window's group *remember* its member: the
     * picked tool already lives in [ToolSelection], so a group that stored a second copy of it could only
     * disagree with the strip's own highlight.
     */
    fun faceFor(selection: ToolSelection): DesktopTool =
        members.firstOrNull { it == selection.current } ?: face
}

/**
 * What a click on a slot means — decided here, before any Compose, so the rule is testable.
 *
 * A click on a slot that is **not** the picked tool picks it. A click on the slot that already *is* the
 * picked tool opens its submenu, which is how a slot's other tools are reached without aiming at the
 * chevron: the first click arms the tool, the second one asks "which of these?" instead of doing nothing —
 * two clicks, never one. A lone tool with no submenu (the pen) answers [Pick] again, a harmless no-op.
 *
 * A **circuit slot** is the case this rule used to miss: its group holds one member (the family is chosen
 * by the slot, not by the picker), so `hasPicker` was false and its palette of parts could never open from
 * the slot at all. [opensSubmenu] is therefore about the slot, not about the member count.
 */
sealed interface SlotClick {
    /** Pick this tool (the slot's face). */
    data class Pick(val tool: DesktopTool) : SlotClick

    /** Open the slot's submenu — the slot is already facing the picked tool. */
    data object OpenPicker : SlotClick
}

/**
 * Whether [this] slot has a submenu: a group with several members, **or** a circuit family's palette.
 *
 * One answer for the three things that need it — the click rule, the chevron, and `Esc`'s closing order —
 * so a slot cannot offer a chevron that opens nothing, or hide the palette of parts it places.
 */
fun ToolGroup.opensSubmenu(): Boolean = hasPicker || members.any { circuitFamilyOf(it) != null }

/** The meaning of a click on [group]'s slot under [selection]. */
fun slotClick(group: ToolGroup, selection: ToolSelection): SlotClick {
    val tool = group.faceFor(selection)
    return if (group.opensSubmenu() && tool == selection.current) SlotClick.OpenPicker else SlotClick.Pick(tool)
}

/**
 * Every slot of both strips, in the order they draw, for the tools the window can act on.
 *
 * Pen and highlighter keep a slot each because nothing shares them; the eraser slot holds its two members
 * (`docs/drawing-tools.md`: the partial eraser cuts the disc's path out of a stroke, the whole-stroke one
 * takes the stroke); the figures share the last slot, so the stem of the rail is the ink you write with,
 * then the things you draw with.
 */
val DESKTOP_TOOL_GROUPS: List<ToolGroup> = listOf(
    ToolGroup(listOf(DesktopTool.PEN)),
    ToolGroup(listOf(DesktopTool.HIGHLIGHTER)),
    ToolGroup(listOf(DesktopTool.ERASER, DesktopTool.ERASER_WHOLE)),
    // Panning as a tool of its own, with the hand the tablet's rail shows (`docs/drawing-tools.md`).
    ToolGroup(listOf(DesktopTool.HAND)),
    ToolGroup(listOf(DesktopTool.VERTICAL_SPACE)),
    // The two ways to select a region on the page: a rectangle and a freehand loop.
    ToolGroup(listOf(DesktopTool.MARQUEE, DesktopTool.LASSO)),
    // Typing on the page: a slot of its own, because a text box is neither ink nor a figure. It carries the
    // tablet's two members — prose and a formula — with Text as its face.
    ToolGroup(listOf(DesktopTool.TEXT, DesktopTool.MATH)),
    // **No audio slot.** The tablet's Play object is reached from the header pill's Play and from `Y`
    // (`DESKTOP_TOOLS_WITHOUT_A_SLOT`): the capsule owns audio on both shells, and a strip button beside
    // it was the same act twice (`docs/audio.md`).
    // Every figure is a slot of its own, as the tablet's rail draws them: one click picks a figure,
    // never a picker. The strip scrolls, so the seven slots cost no reachability.
    ToolGroup(listOf(DesktopTool.LINE)),
    ToolGroup(listOf(DesktopTool.ARROW)),
    ToolGroup(listOf(DesktopTool.DOUBLE_ARROW)),
    ToolGroup(listOf(DesktopTool.RECTANGLE)),
    ToolGroup(listOf(DesktopTool.ELLIPSE)),
    // The tablet's STEM figures, one **submenu** as on the tablet: a triangle and a trapezoid draw the
    // variant Settings ▸ Figures names, the polygon is one drag, and one slot keeps the rail as long as
    // it was. A pentagon or a hexagon is that same polygon at five or six sides — the tablet stopped
    // giving them buttons of their own, and so does the strip.
    ToolGroup(
        listOf(
            DesktopTool.TRIANGLE,
            DesktopTool.TRAPEZOID,
            DesktopTool.POLYGON,
        ),
    ),
    ToolGroup(listOf(DesktopTool.AXES)),
    // The STEM slots: each places a **living element** (a re-editable recipe), not ink, so each keeps a
    // slot of its own rather than sharing a picker — a plot, a table and a component are three tools, the
    // same way the pen and the highlighter are.
    ToolGroup(listOf(DesktopTool.PLOT)),
    ToolGroup(listOf(DesktopTool.TABLE)),
    // The tablet's three circuit palettes, one slot each (`CircuitFamily`): a merged slot would hide
    // which family a part belongs to, which is how the tablet's secondary toolbar draws them.
    ToolGroup(listOf(DesktopTool.CIRCUIT)),
    ToolGroup(listOf(DesktopTool.CIRCUIT_ACTIVE)),
    ToolGroup(listOf(DesktopTool.CIRCUIT_LOGIC)),
)

/**
 * The tools the window's canvas **acts on** today — the map of
 * `desktop-every-tool-does-what-it-does-on-the-tablet`, in code.
 *
 * A tool leaves this set the moment its behaviour lands, which is also the moment it may join a group:
 * the selection pair arrived with `desktop-eraser-and-selection-on-shared-geometry`, the living entities
 * (Plot, Table, Circuit) with `desktop-stem-slot-placement`, and [DesktopTool.TEXT] with
 * `desktop-text-slot-and-in-place-field` — the last slot of the strip that did nothing, which is what
 * closes `desktop-every-tool-does-what-it-does-on-the-tablet`, and the audio tap arrived with
 * `desktop-audio-tap-and-y-key` — the pill's Play and `Y` on the keyboard.
 *
 * It is **not** the set of slots: [DESKTOP_TOOLS_WITHOUT_A_SLOT] names the acting tools another surface
 * owns, so "acts" and "has a button on the strip" are two different claims.
 */
val DESKTOP_TOOLS_THAT_ACT: Set<DesktopTool> = setOf(
    DesktopTool.PEN,
    DesktopTool.HIGHLIGHTER,
    // The hand moves the view with a primary drag (`pageGestureLoop`), so it acts on the page.
    DesktopTool.HAND,
    DesktopTool.ERASER,
    DesktopTool.ERASER_WHOLE,
    DesktopTool.VERTICAL_SPACE,
    DesktopTool.MARQUEE,
    DesktopTool.LASSO,
    // The Text slot types in place: a press opens a box, and the commit writes one element.
    DesktopTool.TEXT,
    // Its second member asks for a formula's source and writes one math block.
    DesktopTool.MATH,
    // The audio tap replays the recording a stroke was drawn over — a click on the page, no ink. It acts
    // from the header pill (and `Y`), so it is not in the slots (see `DESKTOP_TOOLS_WITHOUT_A_SLOT`).
    DesktopTool.AUDIO,
    DesktopTool.LINE,
    DesktopTool.ARROW,
    DesktopTool.DOUBLE_ARROW,
    DesktopTool.RECTANGLE,
    DesktopTool.ELLIPSE,
    // The STEM figures the tablet has and the window did not: a drag each, written as one ordinary
    // stroke, so they need no element of their own.
    DesktopTool.TRIANGLE,
    DesktopTool.TRAPEZOID,
    DesktopTool.POLYGON,
    DesktopTool.AXES,
    // The STEM slots place a living element through `LivingPlacement` — one element, one undo step.
    DesktopTool.PLOT,
    DesktopTool.TABLE,
    DesktopTool.CIRCUIT,
    DesktopTool.CIRCUIT_ACTIVE,
    DesktopTool.CIRCUIT_LOGIC,
)

/**
 * The acting tools the strip does **not** offer, because another surface owns them.
 *
 * The tablet's **Play object** ([DesktopTool.AUDIO]) is the one: its door is the audio pill's Play —
 * the header capsule owns audio, exactly as `AudioMiniCapsule` does on the tablet, and `Y` — so a strip
 * button beside it was the same act twice. Removing it is a decision about **where** a tool is reached,
 * not about whether it works: the tool still acts on the page (`docs/audio.md`).
 */
val DESKTOP_TOOLS_WITHOUT_A_SLOT: Set<DesktopTool> = setOf(DesktopTool.AUDIO)

/**
 * [DESKTOP_TOOL_GROUPS] in the reader's own order.
 *
 * The order is stored as the slots' **face tool** names, so it survives a build that renames or re-groups
 * something: a name the catalogue no longer has is skipped, and a slot the stored order does not mention
 * follows in its factory place — which is what keeps a tool added by a later build from being hidden by an
 * arrangement written before it existed. Empty is the factory order, not "no tools".
 *
 * Slots the same name repeats are taken once: the strips are an arrangement, not a list a bug can double.
 */
fun orderedToolGroups(order: List<String>): List<ToolGroup> {
    if (order.isEmpty()) return DESKTOP_TOOL_GROUPS
    val byName = DESKTOP_TOOL_GROUPS.associateBy { it.face.name }
    val picked = order.mapNotNull { byName[it] }.distinct()
    return picked + DESKTOP_TOOL_GROUPS.filter { group -> group !in picked }
}

/** The slot [tool] lives in, or null when the strip does not show it (yet). */
fun groupOf(tool: DesktopTool): ToolGroup? = DESKTOP_TOOL_GROUPS.firstOrNull { tool in it.members }

/**
 * Which picker `Alt + Down` opens, given the picker that is already open: the picked tool's group, or
 * **null** when the same key should close it.
 *
 * The rule lives here, not in the key handler, so the window's keyboard can be tested on the JVM: a
 * toggle that is off for a lone tool (the pen has nothing to choose) and closes the picker when it is
 * already up is exactly the kind of thing a `when` inside a composable would hide.
 */
fun toggledPicker(open: Int?, selection: ToolSelection): Int? {
    val index = DESKTOP_TOOL_GROUPS.indexOfFirst { selection.current in it.members }
    if (index < 0) return null
    if (!DESKTOP_TOOL_GROUPS[index].hasPicker) return null
    return if (open == index) null else index
}
