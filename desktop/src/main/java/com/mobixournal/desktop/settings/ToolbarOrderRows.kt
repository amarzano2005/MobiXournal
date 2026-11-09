package com.mobixournal.desktop.settings
import com.mobixournal.desktop.tools.StripItem
import com.mobixournal.desktop.tools.stripItems

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The **Buttons** group's rows, each tool slot carrying a drag handle.
 *
 * The drag is the page list's, in miniature: the carried row is lifted over the list on its shadow,
 * follows the pointer, the rows between glide open on a short tween and the row it would land on
 * wears the accent along its top edge. A drop commits the whole arrangement through
 * `StripOrderState.moveSlotTo`, so both strips and the next launch read exactly what the list showed.
 *
 * A slot row draws **no chevrons**: the same handle answers the keyboard, and focused it steps the
 * slot one place with an arrow key. Insert image keeps its own pair — its place is an anchor under a
 * slot, not an index in the order, so it has no grip to carry it — and the stroke settings travel
 * with the highlighter rather than getting a row of their own.
 */
@Composable
internal fun ToolbarOrderRows(policy: DesktopSettingsState) {
    val groups = policy.toolGroups
    val drag = remember { ReorderState() }

    // The rows are the strip's own contents in the strip's own order ([stripItems]): the list said
    // "bottom of the strip" while the strip drew Insert image under Text.
    for (item in stripItems(groups, policy.imageAfter)) when (item) {
        // The stroke's settings travel with the highlighter; they are not a button, so they get no row.
        StripItem.Stroke -> Unit
        is StripItem.Tools -> {
            val index = item.index
            ReorderRow(drag, index, groups.size) {
                SettingsRow(
                    label = item.group.face.label,
                    // Only a slot holding more than one tool says what it holds: "Pen — Pen" teaches nothing.
                    note = item.group.members.takeIf { it.size > 1 }?.joinToString(" · ") { it.label }.orEmpty(),
                ) {
                    DragGrip(
                        label = item.group.face.label,
                        onDragStart = { drag.start(index) },
                        onDrag = drag::by,
                        onDragEnd = { drag.drop(groups.size)?.let { to -> policy.strips.moveSlotTo(index, to) } },
                        onDragCancel = drag::clear,
                        onStep = { direction -> policy.moveToolSlot(index, direction) },
                    )
                }
            }
        }
        StripItem.Image -> SettingsRow(
            label = "Insert image",
            note = "Follows the tool slot directly above it.",
        ) {
            val anchor = groups.indexOfFirst { it.face.name == policy.imageAfter }
            SettingsStepButtons(
                upEnabled = anchor > 0,
                downEnabled = anchor in 0 until groups.lastIndex,
                onUp = { policy.moveImageSlot(-1) },
                onDown = { policy.moveImageSlot(1) },
            )
        }
    }
}
