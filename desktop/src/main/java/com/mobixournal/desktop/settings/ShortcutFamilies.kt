package com.mobixournal.desktop.settings

import com.mobixournal.desktop.shell.ShellCommand
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.TOGGLE_COMMANDS

/**
 * **The key table, by family** — the order the Shortcuts page reads in.
 *
 * The table itself is one flat list of sixty-odd rows, which is what the keyboard needs and what a reader
 * cannot scan: *Undo* standing next to *Show the pages* and *Logic gates* is a list sorted by the day each
 * entry was added. This is the reading order, and it is **also** the order the strips and the STEM dock
 * already use — ink first, then what a stroke is made of, then the shell's own commands — so a reader who
 * knows where the lasso lives in the rail finds its key in the same place here.
 *
 * It is data, not a second table: [of] only *sorts* the entries the keyboard resolves through, and every row
 * lands in exactly one family (the test says so), so a command added tomorrow cannot quietly vanish from the
 * page. A row nobody has placed yet falls into *Other* rather than into no list at all.
 *
 * [matches] is the page's search: a tool's name, a command's words or the chord it holds — so *Alt+B* finds
 * the ring and *eras* finds both erasers, which is how a reader who remembers only half of what they want
 * still gets to it.
 */
internal object ShortcutFamilies {

    /** One heading of the page: a family's name and the rows under it, in the family's own order. */
    data class Family(val title: String, val entries: List<ShortcutEntry>)

    private val INK = listOf(
        DesktopTool.PEN,
        DesktopTool.HIGHLIGHTER,
        DesktopTool.ERASER,
        DesktopTool.ERASER_WHOLE,
    )

    private val SELECTION_AND_TEXT = listOf(
        DesktopTool.HAND,
        DesktopTool.VERTICAL_SPACE,
        DesktopTool.LASSO,
        DesktopTool.MARQUEE,
        DesktopTool.PDF_TEXT_SELECT,
        DesktopTool.TEXT,
        DesktopTool.MATH,
        DesktopTool.AUDIO,
    )

    private val SHAPES = listOf(
        DesktopTool.LINE,
        DesktopTool.ARROW,
        DesktopTool.DOUBLE_ARROW,
        DesktopTool.RECTANGLE,
        DesktopTool.ELLIPSE,
        DesktopTool.TRIANGLE,
        DesktopTool.TRAPEZOID,
        DesktopTool.POLYGON,
    )

    private val STEM = listOf(
        DesktopTool.PLOT,
        DesktopTool.AXES,
        DesktopTool.TABLE,
        DesktopTool.CIRCUIT,
        DesktopTool.CIRCUIT_ACTIVE,
        DesktopTool.CIRCUIT_LOGIC,
    )

    private val GUIDES = listOf(DesktopTool.SETSQUARE, DesktopTool.COMPASS)

    private val DOCUMENT = listOf(
        ShellCommand.OPEN,
        ShellCommand.SAVE,
        ShellCommand.SAVE_AS,
        ShellCommand.EXPORT_PDF,
        ShellCommand.FIND,
        ShellCommand.OPEN_SETTINGS,
        ShellCommand.CLOSE_TAB,
    )

    private val EDITING = listOf(
        ShellCommand.UNDO,
        ShellCommand.REDO,
        ShellCommand.CUT_SELECTION,
        ShellCommand.COPY_SELECTION,
        ShellCommand.PASTE_CLIPBOARD,
        ShellCommand.DUPLICATE_SELECTION,
        ShellCommand.DELETE_SELECTION,
    )

    private val VIEW = listOf(
        ShellCommand.TOGGLE_SIDEBAR,
        ShellCommand.ZOOM_IN,
        ShellCommand.ZOOM_OUT,
        ShellCommand.FIT_PAGE,
        ShellCommand.FIT_WIDTH,
        ShellCommand.NEXT_PAGE,
        ShellCommand.PREVIOUS_PAGE,
        ShellCommand.NEXT_TAB,
        ShellCommand.PREVIOUS_TAB,
        ShellCommand.START_PAN,
        ShellCommand.TOGGLE_FULLSCREEN,
    )

    private val PEN = listOf(ShellCommand.RADIAL_HUD, ShellCommand.DISMISS_FAILURE)

    /** The three switches a stylus button presses: the row carries its own member, so it leads the page. */
    private val TOGGLES = ShellCommand.entries.filter { it in TOGGLE_COMMANDS }

    /** The families the page draws, in reading order, each with only the rows [entries] holds. */
    fun of(entries: List<ShortcutEntry>): List<Family> {
        val tools = { wanted: List<DesktopTool> -> entries.filter { it.tool in wanted } }
        val commands = { wanted: List<ShellCommand> -> entries.filter { it.command in wanted } }
        val placedTools = INK + SELECTION_AND_TEXT + SHAPES + STEM + GUIDES
        val placedCommands = TOGGLES + DOCUMENT + EDITING + VIEW + PEN
        // A circuit slot's parts follow their own slot: the palette is the slot's, so its rows sit under it.
        val stem = STEM.flatMap { slot -> entries.filter { it.tool == slot } }
        val rest = entries.filter { it.tool !in placedTools && it.command !in placedCommands }
        return listOf(
            Family("Toggle", commands(TOGGLES)),
            Family("Ink", tools(INK)),
            Family("Selection and text", tools(SELECTION_AND_TEXT)),
            Family("Shapes", tools(SHAPES)),
            Family("STEM", stem),
            Family("Guides", tools(GUIDES)),
            Family("Document", commands(DOCUMENT)),
            Family("Editing", commands(EDITING)),
            Family("View and pages", commands(VIEW)),
            Family("The pen", commands(PEN)),
            Family("Other", rest),
        ).filter { it.entries.isNotEmpty() }
    }

    /**
     * Whether [entry] answers [query]: its name, its chord as this window holds it, or the tool it picks.
     *
     * Blank finds everything, which is the page at rest. [key] is the row's binding from the live table, not
     * its factory one, so searching *Alt+B* finds the ring after a remap too.
     */
    fun matches(entry: ShortcutEntry, query: String, key: String = entry.defaultKey): Boolean {
        val wanted = query.trim()
        if (wanted.isEmpty()) return true
        val haystack = listOfNotNull(entry.label, key, entry.tool?.name, entry.command?.name)
        return haystack.any { it.contains(wanted, ignoreCase = true) }
    }
}
