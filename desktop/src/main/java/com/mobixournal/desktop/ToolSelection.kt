package com.mobixournal.desktop

/**
 * The drawing tools of `docs/ui-modernization.md` §3, in the one order every strip shows them.
 *
 * The order is the doc's own — core inking first, then geometry, then STEM — and it lives in the enum
 * rather than in each strip, so the rail and the ribbon cannot end up offering the same tools in a
 * different order. [shortcut] is the accelerator the shortcuts table assigns and the one the hover pill
 * prints; [ToolStrips] draws [label] beside it.
 *
 * Plain Kotlin, no Compose types: what a tool *is* must be answerable in a JVM test with no window,
 * which is also why the key handling ([toolForKey]) derives from these strings instead of holding a
 * second table.
 */
enum class DesktopTool(val label: String, val shortcut: String) {
    PEN("Pen", "B"),
    HIGHLIGHTER("Highlighter", "H"),
    ERASER("Eraser", "E"),
    // The eraser's second member (`docs/drawing-tools.md`): a touched stroke goes whole, where the
    // partial eraser cuts the disc's path out of it.
    ERASER_WHOLE("Eraser (whole)", "Shift+E"),
    VERTICAL_SPACE("Vertical space", "Shift+V"),
    LASSO("Lasso", "V"),
    MARQUEE("Marquee", "S"),
    TEXT("Text", "X"),
    // The tablet's Text group carries both halves of typing on the page: prose and a formula. Here they
    // share the slot too, so the window has the tablet's two members rather than a second strip entry.
    MATH("Formula", "Shift+T"),
    LINE("Line", "L"),
    ARROW("Arrow", "A"),
    DOUBLE_ARROW("Double arrow", "Shift+A"),
    RECTANGLE("Rectangle", "R"),
    ELLIPSE("Ellipse", "C"),
    SPLINE("Spline", "P"),
    PLOT("Plot", "M"),
    AXES("Axes", "Shift+X"),
    TABLE("Table", "T"),
    // The tablet's **three** circuit palettes (`CircuitFamily`), one slot each: the passive parts, the
    // active ones and the gates. One merged "Circuit" slot would hide which family a part belongs to,
    // which is how the tablet's own secondary toolbar draws it.
    CIRCUIT("Passive circuits", "K"),
    CIRCUIT_ACTIVE("Active circuits", "Shift+K"),
    CIRCUIT_LOGIC("Logic gates", "Alt+K"),
}

/**
 * The picked tool, as a **value**: picking one hands back the next selection instead of mutating this
 * one, so the shell can hold it in Compose snapshot state and a test can compare two selections
 * without watching for a change notification.
 *
 * Nothing here draws yet — the strips only set a selection. What the pen does with it arrives with the
 * desktop editing phase (`desktop-editing-undo-and-save`).
 */
data class ToolSelection(val current: DesktopTool = DesktopTool.PEN) {

    /** The selection with [tool] picked; the same object when it already is. */
    fun select(tool: DesktopTool): ToolSelection = if (tool == current) this else copy(current = tool)
}

/** The tool a shortcut names (`"M"`, `"Shift+X"`), or null when no tool claims it. */
fun toolForShortcut(shortcut: String): DesktopTool? =
    DesktopTool.entries.firstOrNull { it.shortcut.equals(shortcut.trim(), ignoreCase = true) }
