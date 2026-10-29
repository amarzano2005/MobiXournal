package com.mobixournal.desktop

import com.mobixournal.design.ToolGlyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet's **Play object** as a window slot: it is on a strip, it is not ink, and `Y` picks it.
 *
 * The slot test is deliberately about *what a click means* rather than about pixels: a tap tool that
 * claimed the pen's packets would ink the page instead of replaying it, and a tool missing from
 * [DESKTOP_TOOLS_THAT_ACT] would be a slot the strip must not offer at all
 * (`desktop-every-tool-does-what-it-does-on-the-tablet`'s rule, checked by `ToolGroupsTest`).
 */
class DesktopAudioToolTest {

    @Test
    fun `the audio tap has a slot, a picture and no ink`() {
        val groups = DESKTOP_TOOL_GROUPS.filter { DesktopTool.AUDIO in it.members }
        assertEquals("one slot, not two", 1, groups.size)
        assertTrue("and it is a slot the strip may offer", DesktopTool.AUDIO in DESKTOP_TOOLS_THAT_ACT)
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
