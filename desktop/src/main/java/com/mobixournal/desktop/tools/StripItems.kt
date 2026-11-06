package com.mobixournal.desktop.tools
import com.mobixournal.desktop.io.ImageSlot

import androidx.compose.runtime.Composable

/**
 * What a strip draws between its two dividers, in the order it draws them: a tool's slot, the stroke's
 * settings, or Insert image.
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
}

/**
 * The slot **Insert image** stands under on a fresh install: the PDF text select slot, where the factory
 * order draws it — a picture is placed beside the commands that read or type on the page, not among the
 * ink. One value, so the store's default and the strips' fallback cannot disagree.
 */
internal val DEFAULT_IMAGE_ANCHOR: DesktopTool = DesktopTool.PDF_TEXT_SELECT

/**
 * The strip's contents, in the order they draw.
 *
 * [imageAfter] is the strip's **one movable command**: Insert image stands under the slot the reader put it
 * under, so moving the button is a choice of anchor rather than a second arrangement to keep in step. An
 * anchor this arrangement no longer holds — a slot taken out of the strips on another machine — falls back
 * to [DEFAULT_IMAGE_ANCHOR], where a fresh install keeps it, instead of dropping the button off the strip.
 */
internal fun stripItems(
    groups: List<ToolGroup>,
    imageAfter: String = DEFAULT_IMAGE_ANCHOR.name,
): List<StripItem> = buildList {
    val anchor = imageAfter.takeIf { name -> groups.any { it.face.name == name } }
        ?: DEFAULT_IMAGE_ANCHOR.name
    groups.forEachIndexed { index, group ->
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
        is StripItem.Tools -> Unit
    }
}
