package com.mobixournal.desktop.tools
import com.mobixournal.desktop.io.ImageSlot

import androidx.compose.runtime.Composable

/**
 * What a strip draws between its two ends, in the order it draws them: a tool's slot, the stroke's
 * settings, Insert image, or the rule between two tool families.
 *
 * Two commands stand **among** the tools rather than after them. The stroke's settings belong under the
 * highlighter whose ink they answer for — a colour, a pattern and a width are that tool's own properties —
 * and Insert image follows the slot it was put under ([DEFAULT_IMAGE_ANCHOR] until the reader moves it, and
 * whatever the Toolbar section's own row moves it to), because a picture is placed beside the commands that
 * put content on the page. The stroke settings travel with the highlighter wherever the reader drags it
 * (`orderedToolGroups`), so the pairing survives a rearrangement instead of being a position someone has to
 * keep.
 *
 * The list is the one value the strips iterate and the render tests measure, so a slot's place cannot be
 * right in the code and wrong in a test that counted the buttons by hand.
 */
internal sealed interface StripItem {

    /** A tool's slot, with its group's index in the arrangement — the key its picker is open on. */
    data class Tools(val group: ToolGroup, val index: Int) : StripItem

    /** The stroke's colour, pattern and width, under the highlighter. */
    data object Stroke : StripItem

    /** Insert image, under the Text slot. */
    data object Image : StripItem

    /**
     * The **rule between two tool families** — one 1 dp hairline, 24 dp long, with the strip's own gap of
     * air either side.
     *
     * Twenty slots in one run read as one long button bar; the five families (`StripFamily`) are what the
     * reader is actually choosing between — the ink, the selection and the page commands, the figures, the
     * STEM slot-elements, the guides — so the strip says where one ends. The rule is an *item* rather than
     * a gap on a slot, so the strips iterate one list and the render tests measure one list.
     */
    data object Divider : StripItem
}

/**
 * The five families a strip's slots belong to, in the factory order: what you write with, what reads or
 * types on the page, the figures, the STEM slot-elements, and the instruments that rule the page.
 *
 * The families are what [StripItem.Divider] separates. They are drawn from the tool rather than stored on
 * [ToolGroup] because a group is a *slot* (the two erasers share one) while a family is a **run** of
 * slots — and a reader who rearranges the toolbar expects the rule to follow the tools, not a position.
 */
internal enum class StripFamily { INK, SELECT, FIGURES, STEM, GUIDES }

/** Which family a tool's slot belongs to; the strips' rules fall between two of them. */
internal val DesktopTool.stripFamily: StripFamily
    get() = when (this) {
        DesktopTool.PEN, DesktopTool.HIGHLIGHTER,
        DesktopTool.ERASER, DesktopTool.ERASER_WHOLE,
        -> StripFamily.INK

        DesktopTool.HAND, DesktopTool.MARQUEE, DesktopTool.LASSO,
        DesktopTool.TEXT, DesktopTool.MATH, DesktopTool.VERTICAL_SPACE,
        DesktopTool.PDF_TEXT_SELECT, DesktopTool.AUDIO,
        -> StripFamily.SELECT

        DesktopTool.LINE, DesktopTool.ARROW, DesktopTool.DOUBLE_ARROW,
        DesktopTool.RECTANGLE, DesktopTool.ELLIPSE,
        DesktopTool.TRIANGLE, DesktopTool.TRAPEZOID, DesktopTool.POLYGON,
        -> StripFamily.FIGURES

        DesktopTool.PLOT, DesktopTool.AXES, DesktopTool.TABLE,
        DesktopTool.CIRCUIT, DesktopTool.CIRCUIT_ACTIVE, DesktopTool.CIRCUIT_LOGIC,
        -> StripFamily.STEM

        DesktopTool.SETSQUARE, DesktopTool.COMPASS -> StripFamily.GUIDES
    }

/**
 * The slot **Insert image** stands under on a fresh install: the marquee slot, where the factory order draws
 * it — a picture is placed beside the commands that select or type on the page, not among the ink. One value,
 * so the store's default and the strips' fallback cannot disagree. It replaced the retired PDF text slot,
 * which no strip draws any more, so a fresh install keeps the button on a slot that exists.
 */
internal val DEFAULT_IMAGE_ANCHOR: DesktopTool = DesktopTool.MARQUEE

/**
 * The strip's contents, in the order they draw.
 *
 * [imageAfter] is the strip's **one movable command**: Insert image stands under the slot the reader put it
 * under, so moving the button is a choice of anchor rather than a second arrangement to keep in step. An
 * anchor this arrangement no longer holds — a slot taken out of the strips on another machine — falls back
 * to [DEFAULT_IMAGE_ANCHOR], where a fresh install keeps it, instead of dropping the button off the strip.
 *
 * A [StripItem.Divider] is added whenever the run of slots changes family, which is what turns one long
 * band of buttons into five groups the eye can name.
 */
internal fun stripItems(
    groups: List<ToolGroup>,
    imageAfter: String = DEFAULT_IMAGE_ANCHOR.name,
): List<StripItem> = buildList {
    val anchor = imageAfter.takeIf { name -> groups.any { it.face.name == name } }
        ?: DEFAULT_IMAGE_ANCHOR.name
    var family: StripFamily? = null
    groups.forEachIndexed { index, group ->
        val next = group.face.stripFamily
        if (family != null && next != family) add(StripItem.Divider)
        family = next
        add(StripItem.Tools(group, index))
        if (group.face == DesktopTool.HIGHLIGHTER) add(StripItem.Stroke)
        if (group.face.name == anchor) add(StripItem.Image)
    }
}

/** The two commands a strip draws outside its tool list, each in the material of the slot that owns it. */
@Composable
internal fun StripCommand(
    item: StripItem,
    state: ToolStripState,
    ink: InkActions,
    side: StripSide,
) {
    when (item) {
        StripItem.Stroke -> StrokeSlot(
            ink = state.ink,
            palette = state.palette,
            custom = state.custom,
            favourites = state.favourites,
            onPick = ink.onColour,
            onWidth = ink.onWidth,
            onLineStyle = ink.onLineStyle,
            onEditCustom = ink.onEditCustom,
            onAddColour = ink.onAddColour,
            side = side,
        )

        StripItem.Image -> ImageSlot(state.onInsertImage, side)
        is StripItem.Tools, StripItem.Divider -> Unit
    }
}
