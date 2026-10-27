package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.LineStyle
import com.mobixournal.render.InkGeometry

/**
 * The window's **ink**: the two tools' colours, widths and stroke patterns, the palette they share and
 * the three favourites each one picks from.
 *
 * It is a piece of the shell's state rather than a second one: [DesktopShellState] owns it and forwards
 * every member, so `state.ink`, `state.pickColour(…)` and the colour panel read exactly what they read
 * before, while the commands that keep the settings file in step live here
 * (`SourceStructureTest` keeps `DesktopShell.kt` inside its budget).
 *
 * The picked tool is a **lambda**, not a copy: the ink is always the ink of the tool in hand, so a
 * second copy of "the current ink" could only be a copy that disagrees with the tool.
 */
internal class DesktopInkState(
    private val settings: DesktopSettings,
    private val currentTool: () -> DesktopTool,
) {

    /**
     * The two inks the window holds — the pen's and the highlighter's — restored from the settings file.
     */
    private var inks by mutableStateOf(
        DesktopInks(
            pen = DesktopInk(
                colour = settings.inkColour,
                width = settings.inkWidth,
                lineStyle = settings.inkLineStyle,
            ),
            highlighter = DesktopInk(
                colour = settings.highlighterColour,
                width = settings.highlighterWidth,
                lineStyle = settings.highlighterLineStyle,
            ),
        ),
    )

    /** The ink of the picked tool, **as the strips and the settings file hold it** — a slot width. */
    val ink: DesktopInk get() = inks.forTool(currentTool())

    /**
     * The ink the **page** is written with — the same ink, with the highlighter's band scaled.
     *
     * A marker is not a wide pen: the tablet reads the picked S/M/L slot as a highlighter **tip**
     * (`InkGeometry.highlighterWidthFor`: about six times the pen, snapped to the 1 / 3 / 7 mm tips), so
     * the same slot draws a 2 pt line with the pen and an 8.5 pt band with the highlighter. Without this
     * the window's marker was exactly as thick as its pen, which is not what the tool is for. The stored
     * ink stays the slot value, so the strip's dots, the `[`/`]` steps and the settings file are unchanged
     * and both shells read the same rule.
     */
    val pageInk: DesktopInk
        get() = ink.let { picked ->
            if (currentTool() == DesktopTool.HIGHLIGHTER) {
                picked.copy(width = InkGeometry.highlighterWidthFor(picked.width).toFloat())
            } else {
                picked
            }
        }

    /**
     * The **pen's** ink, whatever tool is in hand — what a figure, a circuit or a placement writes with.
     * The tablet draws figures in the pencil's colour even while the highlighter is picked, so a marker
     * never turns a rectangle yellow; the highlighter's ink stays its own case.
     */
    val shapeInk: DesktopInk get() = inks.pen

    /**
     * The palette's editable custom slot, shared by both tools like the palette itself (a palette is what
     * the user has, not what a tool is). Null while it is empty.
     */
    var paletteCustom by mutableStateOf(settings.paletteCustom)
        private set

    /** What the colour panel is editing, or null while it is closed. */
    var editing by mutableStateOf<ColourEditorTarget?>(null)
        private set

    /** The palette the colour flyout offers; the factory eight until the settings file says otherwise. */
    var palette by mutableStateOf(settings.paletteColours)
        private set

    /** The three favourites of each tool, restored from the settings file. */
    private var favs by mutableStateOf(
        DesktopFavourites(pen = settings.colourFavourites, highlighter = settings.highlighterFavourites),
    )

    /** The terna the picked tool picks from — keys `1`–`3` and `Alt`+digit read this one. */
    val favourites: List<Int> get() = favs.forTool(currentTool())

    /**
     * Read the two inks, the palette and its custom slot back from the settings file.
     *
     * The store is the authority on every choice, so an imported settings backup has to reach **this**
     * copy too: the strips and the page draw from it, and refreshing only the settings surface would leave
     * the window painting in the colours the backup replaced.
     */
    fun reload() {
        inks = DesktopInks(
            pen = DesktopInk(
                colour = settings.inkColour,
                width = settings.inkWidth,
                lineStyle = settings.inkLineStyle,
            ),
            highlighter = DesktopInk(
                colour = settings.highlighterColour,
                width = settings.highlighterWidth,
                lineStyle = settings.highlighterLineStyle,
            ),
        )
        palette = settings.paletteColours
        paletteCustom = settings.paletteCustom
        favs = DesktopFavourites(
            pen = settings.colourFavourites,
            highlighter = settings.highlighterFavourites,
        )
        editing = null
    }

    /**
     * Pick the ink colour **of the picked tool**, and remember it: the pen and the highlighter keep their
     * own, so switching to the highlighter never repaints the pencil.
     */
    fun pickColour(colour: Int) {
        val tool = currentTool()
        inks = inks.withInk(tool, inks.forTool(tool).withColour(colour))
        persistInk(tool)
    }

    /** Pick the picked tool's stroke width, and remember it. */
    fun pickWidth(width: Float) {
        val tool = currentTool()
        inks = inks.withInk(tool, inks.forTool(tool).withWidth(width))
        persistInk(tool)
    }

    /** Pick the picked tool's stroke pattern (solid, dashed, dash-dot, dotted), and remember it. */
    fun pickLineStyle(style: LineStyle) {
        val tool = currentTool()
        inks = inks.withInk(tool, inks.forTool(tool).withLineStyle(style))
        persistInk(tool)
    }

    /** Write [tool]'s ink to the settings file — the other tool's entry is not touched. */
    private fun persistInk(tool: DesktopTool) {
        val value = inks.forTool(tool)
        if (tool == DesktopTool.HIGHLIGHTER) {
            settings.highlighterColour = value.colour
            settings.highlighterWidth = value.width
            settings.highlighterLineStyle = value.lineStyle
        } else {
            settings.inkColour = value.colour
            settings.inkWidth = value.width
            settings.inkLineStyle = value.lineStyle
        }
    }

    /** Pick favourite [index] — what keys `1`–`3` do. */
    fun pickFavourite(index: Int) {
        favourites.getOrNull(index)?.let(::pickColour)
    }

    /** Make the colour in use favourite [index] **of the picked tool** — what `Alt`+digit does. */
    fun setFavourite(index: Int) {
        val tool = currentTool()
        val current = favs.forTool(tool)
        if (index !in current.indices) return
        favs = favs.withFavourites(tool, current.toMutableList().also { it[index] = ink.colour })
        if (tool == DesktopTool.HIGHLIGHTER) settings.highlighterFavourites = favs.highlighter
        else settings.colourFavourites = favs.pen
    }

    /**
     * Step the width one slot up ([direction] `+1`, `]`) or down (`-1`, `[`), stopping at the ends
     * rather than wrapping: a thin pen must not jump to the thickest one.
     */
    fun stepWidth(direction: Int) {
        val widths = PenPalette.DEFAULT_WIDTHS
        val slot = PenPalette.widthSlot(ink.width) + direction
        pickWidth(widths[slot.coerceIn(0, widths.lastIndex)])
    }

    /** Open the panel on the custom slot (`CUSTOM`) or on a colour to append (`NEW`). */
    fun editColour(target: ColourEditorTarget) {
        editing = target
    }

    /** Close the panel without committing anything. */
    fun cancelColourEdit() {
        editing = null
    }

    /**
     * Commit what the panel's **Set** handed back: the custom slot for [ColourEditorTarget.CUSTOM], a new
     * palette entry for [ColourEditorTarget.NEW] — and in both cases the colour becomes the ink, which is
     * what picking a swatch would have done.
     */
    fun commitColour(colour: Int) {
        when (editing) {
            ColourEditorTarget.CUSTOM -> {
                paletteCustom = colour
                settings.paletteCustom = colour
                pickColour(colour)
            }
            ColourEditorTarget.NEW -> {
                palette = PenPalette.add(palette, colour)
                settings.paletteColours = palette
                pickColour(colour)
            }
            null -> return
        }
        editing = null
    }
}
