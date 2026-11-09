package com.mobixournal.desktop.settings
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.ToolGroup
import com.mobixournal.desktop.tools.orderedToolGroups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The strips' **own arrangement**, as state: the order of the slots and the one command that follows one.
 *
 * It is a subject of its own for the reason [com.mobixournal.desktop.io.DesktopAudioSettings] is: the order is a
 * topic with its own rules — a move that does not wrap, an anchor that is a name rather than an index, a reset
 * that writes nothing because the factory order *is* the absence of a stored one — and the window's settings
 * state is already the place every topic is glued together (`SourceStructureTest` holds it to its budget).
 *
 * [moveSlot] and [moveImage] are the two edits the Toolbar section makes; both write the store as they go, so
 * a restart draws exactly what this window shows.
 */
internal class StripOrderState(private val store: DesktopSettings) {

    /** The slot order the reader arranged; empty is the factory order ([orderedToolGroups]). */
    var toolOrder by mutableStateOf(store.toolOrder)
        private set

    /** The slot **Insert image** stands under — Text at the factory and on a reset. */
    var imageAfter by mutableStateOf(store.imageAfter)
        private set

    /** The slots in the reader's order — what both strips draw. */
    val groups: List<ToolGroup> get() = orderedToolGroups(toolOrder)

    /**
     * Move slot [index] one place [direction] (‑1 up/left, +1 down/right) and remember the order.
     *
     * The edge is not wrapped: the first slot has nothing above it, and a move past the end is the same
     * arrangement said again. The order is stored as the whole list, so the next launch draws exactly what
     * this window shows.
     */
    fun moveSlot(index: Int, direction: Int) = moveSlotTo(index, index + direction)

    /**
     * Move the slot at [from] straight to [to] — the Toolbar section's **drag**, which lands several
     * places away in one gesture.
     *
     * One write, exactly as a chevron's step is: the arrangement is stored as the whole list, so the
     * strips can only redraw the order the reader just saw. An index past either end, or a drop back
     * where it started, writes nothing.
     */
    fun moveSlotTo(from: Int, to: Int) {
        val groups = groups
        if (from !in groups.indices || to !in groups.indices || from == to) return
        val moved = groups.toMutableList()
        val slot = moved.removeAt(from)
        moved.add(to, slot)
        toolOrder = moved.map { it.face.name }
        store.toolOrder = toolOrder
    }

    /** Put every slot back in the factory order, and forget the arrangement. */
    fun reset() {
        toolOrder = emptyList()
        store.toolOrder = emptyList()
    }

    /**
     * Move Insert image one slot [direction] and remember the anchor.
     *
     * The anchor is a **name**, not an index: the slots are the reader's order, so an index would point at a
     * different neighbour the moment a tool above it moved. The edge is not wrapped, exactly as a slot's own
     * move is not.
     */
    fun moveImage(direction: Int) {
        val groups = groups
        val current = groups.indexOfFirst { it.face.name == imageAfter }
            .takeIf { it >= 0 }
            ?: groups.indexOfFirst { it.face == DesktopTool.TEXT }
        val target = current + direction
        if (current < 0 || target !in groups.indices) return
        imageAfter = groups[target].face.name
        store.imageAfter = imageAfter
    }

    /** Re-read the saved arrangement — a settings import, or every setting reset to its default. */
    fun reload() {
        toolOrder = store.toolOrder
        imageAfter = store.imageAfter
    }
}
