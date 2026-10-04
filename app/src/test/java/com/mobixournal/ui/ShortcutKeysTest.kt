package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-tool and per-colour shortcut maps' SharedPreferences encoding: exact round-trips, the
 * drop-empty rule, and tolerance of foreign strings so a corrupt pref degrades to "no shortcut".
 */
class ShortcutKeysTest {

    @Test
    fun `tool shortcuts round-trip every entry`() {
        val keys = mapOf(EditorTool.PEN to "p", EditorTool.HIGHLIGHTER to "4", EditorTool.TEXIMAGE to "c")
        assertEquals(keys, decodeToolShortcuts(encodeToolShortcuts(keys)))
    }

    @Test
    fun `colour shortcuts round-trip signed ARGB values`() {
        val keys = mapOf(
            XOPP_BLACK to "1",
            XOPP_RED to "2",
            0xFF0A0B0C.toInt() to "#",
        )
        assertEquals(keys, decodeColorShortcuts(encodeColorShortcuts(keys)))
    }

    @Test
    fun `an empty map encodes to an empty string and decodes back to nothing`() {
        assertEquals("", encodeToolShortcuts(emptyMap()))
        assertEquals("", encodeColorShortcuts(emptyMap()))
        assertTrue(decodeToolShortcuts("").isEmpty())
        assertTrue(decodeColorShortcuts("").isEmpty())
    }

    @Test
    fun `blank keys are dropped rather than persisted`() {
        val encoded = encodeToolShortcuts(mapOf(EditorTool.PEN to "", EditorTool.HAND to "h"))
        assertEquals(mapOf(EditorTool.HAND to "h"), decodeToolShortcuts(encoded))
    }

    @Test
    fun `absent or blank raw decodes to nothing`() {
        assertTrue(decodeToolShortcuts(null).isEmpty())
        assertTrue(decodeToolShortcuts("   ").isEmpty())
        assertTrue(decodeColorShortcuts(null).isEmpty())
    }

    @Test
    fun `an unknown tool or colour is dropped and the rest survive`() {
        val raw = "PEN:p,NOT_A_TOOL:q,HIGHLIGHTER:4"
        assertEquals(
            mapOf(EditorTool.PEN to "p", EditorTool.HIGHLIGHTER to "4"),
            decodeToolShortcuts(raw),
        )
        assertEquals(
            mapOf(XOPP_BLACK to "1"),
            decodeColorShortcuts("notanint:1,${XOPP_BLACK}:1"),
        )
    }

    @Test
    fun `a colon as the key still round-trips`() {
        val keys = mapOf(EditorTool.PEN to ":")
        assertEquals(keys, decodeToolShortcuts(encodeToolShortcuts(keys)))
        assertEquals(mapOf(XOPP_RED to ":"), decodeColorShortcuts(encodeColorShortcuts(mapOf(XOPP_RED to ":"))))
    }

    @Test
    fun `resolveKeyString extracts characters, digits, numpad and space`() {
        assertEquals("B", resolveKeyString(unicodeChar = 'b'.code, displayLabel = 'b', keyCode = 30))
        assertEquals("E", resolveKeyString(unicodeChar = 0, displayLabel = 'E', keyCode = 33))
        assertEquals(" ", resolveKeyString(unicodeChar = 0, displayLabel = '\u0000', keyCode = android.view.KeyEvent.KEYCODE_SPACE))
        assertEquals("1", resolveKeyString(unicodeChar = 0, displayLabel = '\u0000', keyCode = android.view.KeyEvent.KEYCODE_1))
        assertEquals("5", resolveKeyString(unicodeChar = 0, displayLabel = '\u0000', keyCode = android.view.KeyEvent.KEYCODE_NUMPAD_5))
        assertEquals("A", resolveKeyString(unicodeChar = 0, displayLabel = '\u0000', keyCode = android.view.KeyEvent.KEYCODE_A))
        assertEquals(null, resolveKeyString(unicodeChar = 0, displayLabel = '\u0000', keyCode = 999))
    }
}
