package com.mobixournal.desktop.tools
import com.mobixournal.desktop.io.ImageSlot

import androidx.compose.runtime.Composable

/**
 * What a strip draws between its two dividers, in the order it draws them: a tool's slot, the stroke's
 * settings, or Insert image.
 *
 * Two commands stand **among** the tools rather than after them. The stroke's settings belong under the
 * highlighter whose ink they answer for — a colour, a pattern and a width are that tool's own properties —
 * and Insert image is the Text slot's neighbour, because a picture is placed the way a text box is. Both
 * follow their tool wherever the reader drags it (`orderedToolGroups`), so the pairing survives a
 * rearrangement instead of being a position someone has to keep.
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
}

/** The strip's contents, in the order they draw. */
internal fun stripItems(groups: List<ToolGroup>): List<StripItem> = buildList {
    groups.forEachIndexed { index, group ->
        add(StripItem.Tools(group, index))
        when (group.face) {
            DesktopTool.HIGHLIGHTER -> add(StripItem.Stroke)
            DesktopTool.TEXT -> add(StripItem.Image)
            else -> Unit
        }
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
        is StripItem.Tools -> Unit
    }
}
