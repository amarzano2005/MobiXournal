package com.mobixournal.desktop

import com.mobixournal.desktop.tools.DEFAULT_IMAGE_ANCHOR
import com.mobixournal.desktop.tools.DESKTOP_TOOL_GROUPS
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.StripItem
import com.mobixournal.desktop.tools.stripFamily
import com.mobixournal.desktop.tools.stripItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strip's one **movable command**: Insert image follows the slot it was put under.
 *
 * The button used to be welded to Text by the same `when` that placed the stroke's settings under the
 * highlighter, so "move it" had no answer at all. The anchor is a slot's name ([stripItems]'s `imageAfter`),
 * which is what these tests hold: a chosen anchor places the row after that slot, and an anchor this
 * arrangement does not hold puts it back on the default anchor instead of dropping it off the strip.
 */
class StripItemsTest {

    /** The image's index and its anchor's, read from **one** arrangement — indices shift with the anchor. */
    private fun indices(anchor: String): Pair<Int, Int> {
        val items = stripItems(DESKTOP_TOOL_GROUPS, anchor)
        val image = items.indexOf(StripItem.Image)
        val slot = items.indexOfFirst { it is StripItem.Tools && it.group.face.name == anchor }
        return image to slot
    }

    private fun imageIndexAfter(anchor: String): Int =
        stripItems(DESKTOP_TOOL_GROUPS, anchor).indexOf(StripItem.Image)

    @Test
    fun `insert image follows the slot it was put under`() {
        // Three real slot faces: an unknown name is the fallback case below, not an anchor. The retired PDF
        // text slot is not one of them any more — `DEFAULT_IMAGE_ANCHOR` and this list moved to slots the
        // strips actually draw.
        for (anchor in listOf(DesktopTool.TEXT, DesktopTool.MARQUEE, DesktopTool.ERASER)) {
            val (image, slot) = indices(anchor.name)
            assertTrue("the image slot is on the strip under $anchor", image > 0)
            assertEquals("it stands right after $anchor", slot + 1, image)
        }
    }

    /**
     * The rules fall **between** the families: four dividers for the five families of the factory order,
     * each one between two slots that belong to different families, and none inside a family's run.
     */
    @Test
    fun `the rules fall between the tool families`() {
        val items = stripItems(DESKTOP_TOOL_GROUPS)
        assertEquals("four rules separate five families", 4, items.count { it is StripItem.Divider })
        for (index in items.indices) {
            if (items[index] !is StripItem.Divider) continue
            val before = items.take(index).filterIsInstance<StripItem.Tools>().last().group.face.stripFamily
            val after = items.drop(index + 1).filterIsInstance<StripItem.Tools>().first().group.face.stripFamily
            assertNotEquals("a rule must not fall inside one family", before, after)
        }
    }

    @Test
    fun `a slot this arrangement does not hold puts the image on the default anchor`() {
        assertEquals(
            "an unknown anchor is the default, not a missing button",
            imageIndexAfter(DEFAULT_IMAGE_ANCHOR.name),
            imageIndexAfter("NO_SUCH_SLOT"),
        )
        assertTrue("and the strip still carries it", imageIndexAfter("NO_SUCH_SLOT") > 0)
    }

    @Test
    fun `explicit divider tokens override auto-family rules and NO_DIVIDERS drops all rules`() {
        val customOrder = listOf("PEN", "DIV_1", "HIGHLIGHTER", "DIV_2", "DIV_3", "ERASER")
        val items = stripItems(DESKTOP_TOOL_GROUPS, rawOrder = customOrder)
        assertEquals("consecutive DIV_2/DIV_3 collapse into one rule", 2, items.count { it is StripItem.Divider })

        val noDividers = stripItems(
            DESKTOP_TOOL_GROUPS,
            rawOrder = listOf("PEN", "HIGHLIGHTER", "NO_DIVIDERS"),
        )
        assertEquals("NO_DIVIDERS produces zero rules", 0, noDividers.count { it is StripItem.Divider })
    }
}
