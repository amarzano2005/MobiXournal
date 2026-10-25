package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strips' **groups**: one slot per family, a chevron on the ones that hold more than a tool, and every
 * member reachable by the key it prints.
 *
 * The claim this file protects is the map's own: a slot is only shown when the canvas acts on it, so the
 * window can no longer offer a Text or a Circuit that does nothing. Two drifts fail here — a group listing
 * a tool the canvas ignores, and an acting tool that no group offers (a tool the user can only reach by
 * knowing its letter).
 */
class ToolGroupsTest {

    @Test
    fun `every slot holds tools the window acts on`() {
        for (group in DESKTOP_TOOL_GROUPS) {
            for (tool in group.members) {
                assertTrue("$tool has a slot but the canvas ignores it", tool in DESKTOP_TOOLS_THAT_ACT)
            }
        }
    }

    @Test
    fun `every acting tool has a slot`() {
        val offered = DESKTOP_TOOL_GROUPS.flatMap { it.members }.toSet()
        assertEquals(
            "these tools act but no slot offers them",
            emptySet<DesktopTool>(),
            DESKTOP_TOOLS_THAT_ACT - offered,
        )
    }

    @Test
    fun `no tool is offered twice`() {
        val offered = DESKTOP_TOOL_GROUPS.flatMap { it.members }
        assertEquals("a tool sits in two slots", offered.size, offered.toSet().size)
    }

    @Test
    fun `a group faces its first member until one of its own is picked`() {
        val erasers = groupOf(DesktopTool.ERASER_WHOLE)!!
        assertSame("the group is reached from either member", erasers, groupOf(DesktopTool.ERASER))
        assertEquals(DesktopTool.ERASER, erasers.face)
        assertEquals(
            "an unpicked group shows its face",
            DesktopTool.ERASER,
            erasers.faceFor(ToolSelection(DesktopTool.PEN)),
        )
        assertEquals(
            "the picked member is the slot's face",
            DesktopTool.ERASER_WHOLE,
            erasers.faceFor(ToolSelection(DesktopTool.ERASER_WHOLE)),
        )
    }

    @Test
    fun `only a group with more than one member carries a picker`() {
        assertFalse("the pen has nothing to pick from", groupOf(DesktopTool.PEN)!!.hasPicker)
        assertTrue(groupOf(DesktopTool.ERASER)!!.hasPicker)
        assertFalse(
            "every figure is a slot of its own, so it carries no picker",
            groupOf(DesktopTool.SPLINE)!!.hasPicker,
        )
    }

    /**
     * The scope's own done condition: **no slot is dead**. Every tool the enum names now acts and is
     * offered — Text was the last one waiting (`desktop-text-slot-and-in-place-field`) — so a new tool
     * added without its behaviour fails here rather than being drawn as a button that does nothing.
     */
    @Test
    fun `every tool in the strip acts, so no slot is dead`() {
        val offered = DESKTOP_TOOL_GROUPS.flatMap { it.members }.toSet()
        assertEquals("every tool is offered", DesktopTool.entries.toSet(), offered)
        assertEquals("and every one acts", DesktopTool.entries.toSet(), DESKTOP_TOOLS_THAT_ACT)
        assertNotNull("Text included", groupOf(DesktopTool.TEXT))
    }

    @Test
    fun `alt and down opens the picked tool's picker and closes it again`() {
        assertTrue("Alt+Down is the picker's key", toolPickerKey(Key.DirectionDown, alt = true))
        assertFalse("a bare Down is next page", toolPickerKey(Key.DirectionDown, alt = false))

        val erasers = DESKTOP_TOOL_GROUPS.indexOfFirst { DesktopTool.ERASER in it.members }
        val eraser = ToolSelection(DesktopTool.ERASER)
        assertEquals("the key opens the group of the picked tool", erasers, toggledPicker(null, eraser))
        assertNull("the same key closes it", toggledPicker(erasers, eraser))
        assertNull(
            "a lone tool has no picker to open",
            toggledPicker(null, ToolSelection(DesktopTool.PEN)),
        )
    }

    @Test
    fun `clicking the picked slot opens its picker instead of doing nothing`() {
        val erasers = groupOf(DesktopTool.ERASER)!!
        assertEquals(
            "the first click arms the group's face",
            SlotClick.Pick(DesktopTool.ERASER),
            slotClick(erasers, ToolSelection(DesktopTool.PEN)),
        )
        assertEquals(
            "clicking the slot that is already picked asks which member",
            SlotClick.OpenPicker,
            slotClick(erasers, ToolSelection(DesktopTool.ERASER)),
        )
        assertEquals(
            "the rule follows the member, not the face",
            SlotClick.OpenPicker,
            slotClick(erasers, ToolSelection(DesktopTool.ERASER_WHOLE)),
        )
        assertEquals(
            "a lone tool has no submenu, so a second click is still a pick",
            SlotClick.Pick(DesktopTool.PEN),
            slotClick(groupOf(DesktopTool.PEN)!!, ToolSelection(DesktopTool.PEN)),
        )
    }

    @Test
    fun `a circuit slot's palette is its submenu, and it takes two clicks too`() {
        // The family slot holds one member — the family itself — so `hasPicker` is false and the palette of
        // parts is the slot's real submenu. Without this, a double click on a circuit slot did nothing.
        val circuits = groupOf(DesktopTool.CIRCUIT)!!
        assertTrue("the passive palette is a submenu", circuits.opensSubmenu())
        assertEquals(
            "the first click only arms the slot",
            SlotClick.Pick(DesktopTool.CIRCUIT),
            slotClick(circuits, ToolSelection(DesktopTool.PEN)),
        )
        assertEquals(
            "the second click opens the palette of parts",
            SlotClick.OpenPicker,
            slotClick(circuits, ToolSelection(DesktopTool.CIRCUIT)),
        )
        assertFalse("a lone inking tool has no submenu", groupOf(DesktopTool.PEN)!!.opensSubmenu())
    }

    @Test
    fun `every member of a group is reachable without the picker`() {
        // The rule the picker must not break: it re-faces a slot, it never changes a key.
        for (group in DESKTOP_TOOL_GROUPS) {
            for (member in group.members) {
                assertSame(
                    "${member.label} is only reachable from the picker",
                    member,
                    toolForShortcut(member.shortcut),
                )
            }
        }
    }

    /**
     * The tablet's rail, as the window now draws it: **every figure is a slot**, one click each, and no
     * picker stands between them. A shared figures slot would put six of the seven behind a submenu.
     */
    @Test
    fun `every figure is a slot of its own, in the order the rail draws them`() {
        val figures = listOf(
            DesktopTool.LINE,
            DesktopTool.ARROW,
            DesktopTool.DOUBLE_ARROW,
            DesktopTool.RECTANGLE,
            DesktopTool.ELLIPSE,
            DesktopTool.SPLINE,
            DesktopTool.AXES,
        )
        for (figure in figures) {
            assertEquals("${figure.label} shares a slot", listOf(figure), groupOf(figure)!!.members)
        }
        assertEquals(
            "the figures keep the strip's order, one slot each",
            figures,
            DESKTOP_TOOL_GROUPS.map { it.face }.filter { it in figures.toSet() },
        )
    }
}
