package com.mobixournal.desktop

import com.mobixournal.design.ToolGlyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet's **Play object** on the window: reached from the audio pill's Play and from `Y`, it is not
 * ink, and the tool strip deliberately carries **no** button for it.
 *
 * The claim is about *where* a tool is reached rather than about pixels: a tap tool that claimed the pen's
 * packets would ink the page instead of replaying it, and the strip's second door to audio was the same
 * act twice beside the capsule that owns it (`docs/audio.md`, and `ToolGroupsTest` for the slot rule).
 */
class DesktopAudioToolTest {

    @Test
    fun `the audio tap is reached from the pill, not from a slot`() {
        val slots = DESKTOP_TOOL_GROUPS.filter { DesktopTool.AUDIO in it.members }
        assertEquals("the strip carries no second door to audio", 0, slots.size)
        assertEquals(
            "the pill and `Y` are its doors, named in one place",
            setOf(DesktopTool.AUDIO),
            DESKTOP_TOOLS_WITHOUT_A_SLOT,
        )
        assertTrue("and it still acts on the page", DesktopTool.AUDIO in DESKTOP_TOOLS_THAT_ACT)
        assertEquals("wearing the catalogue's play mark", ToolGlyph.PLAY, DesktopTool.AUDIO.glyph)
        assertNull("a tap lays down no ink", DesktopTool.AUDIO.inkTool())
        assertFalse("so the pen's packets stand aside", DesktopTool.AUDIO.inksByPackets())
    }

    @Test
    fun `a scrub lands inside the clip`() {
        // The bar's ends are the clip's ends: a drag off either side stops there rather than asking the
        // decoder for a negative byte or a frame past the file.
        assertEquals(0, scrubMs(-0.5f, 6_000))
        assertEquals(6_000, scrubMs(1.7f, 6_000))
        assertEquals(1_500, scrubMs(0.25f, 6_000))
        assertEquals("a clip nobody has measured", 0, scrubMs(0.5f, 0))
    }

    @Test
    fun `Y picks the audio tap`() {
        assertEquals("Y", DesktopTool.AUDIO.shortcut)
        assertEquals(DesktopTool.AUDIO, toolForShortcut("Y"))
        // And no other tool answers it, so the table has one meaning per key (`docs/desktop-ui-layout.md`).
        assertEquals(
            listOf(DesktopTool.AUDIO),
            DesktopTool.entries.filter { it.shortcut.equals("Y", ignoreCase = true) },
        )
    }
}
