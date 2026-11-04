package com.mobixournal.desktop

import com.mobixournal.desktop.tools.DESKTOP_TOOL_GROUPS
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.StripItem
import com.mobixournal.desktop.tools.stripItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strip's one **movable command**: Insert image follows the slot it was put under.
 *
 * The button used to be welded to Text by the same `when` that placed the stroke's settings under the
 * highlighter, so "move it" had no answer at all. The anchor is a slot's name ([stripItems]'s `imageAfter`),
 * which is what these tests hold: a chosen anchor places the row after that slot, and an anchor this
 * arrangement does not hold puts it back on Text instead of dropping it off the strip.
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
        // Three real slot faces: an unknown name is the fallback case below, not an anchor.
        for (anchor in listOf(DesktopTool.TEXT, DesktopTool.PEN, DesktopTool.ERASER)) {
            val (image, slot) = indices(anchor.name)
            assertTrue("the image slot is on the strip under $anchor", image > 0)
            assertEquals("it stands right after $anchor", slot + 1, image)
        }
    }

    @Test
    fun `a slot this arrangement does not hold puts the image back on Text`() {
        assertEquals(
            "an unknown anchor is Text, not a missing button",
            imageIndexAfter(DesktopTool.TEXT.name),
            imageIndexAfter("NO_SUCH_SLOT"),
        )
        assertTrue("and the strip still carries it", imageIndexAfter("NO_SUCH_SLOT") > 0)
    }
}
