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
    ToolGroup(listOf(DesktopTool.VERTICAL_SPACE)),
    ToolGroup(
        listOf(
            DesktopTool.LINE,
            DesktopTool.ARROW,
            DesktopTool.DOUBLE_ARROW,
            DesktopTool.RECTANGLE,
            DesktopTool.ELLIPSE,
            DesktopTool.SPLINE,
            DesktopTool.AXES,
        ),
    ),
)

/**
 * The tools the window's canvas **acts on** today — the map of
 * `desktop-every-tool-does-what-it-does-on-the-tablet`, in code.
 *
 * A tool leaves this set the moment its behaviour lands, which is also the moment it may join a group:
 * [DesktopTool.TEXT] arrives with `desktop-shape-text-and-math-tools`, the selection pair with
 * `desktop-eraser-and-selection-on-shared-geometry`, the living entities with
 * `desktop-stem-slot-placement`, and the audio tap with `desktop-audio-replay`.
 */
val DESKTOP_TOOLS_THAT_ACT: Set<DesktopTool> = setOf(
    DesktopTool.PEN,
    DesktopTool.HIGHLIGHTER,
    DesktopTool.ERASER,
    DesktopTool.ERASER_WHOLE,
    DesktopTool.VERTICAL_SPACE,
    DesktopTool.LINE,
    DesktopTool.ARROW,
    DesktopTool.DOUBLE_ARROW,
    DesktopTool.RECTANGLE,
    DesktopTool.ELLIPSE,
    DesktopTool.SPLINE,
    DesktopTool.AXES,
)

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
