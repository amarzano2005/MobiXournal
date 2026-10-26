package com.mobixournal.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The **shared** key table: the format both shells store, the one-key-one-command rule, and the reset.
 *
 * The shells' own defaults are their business (`ShortcutTable` on the desktop, `EditorTool` on the phone);
 * what is pinned here is the part a second implementation could get wrong — a displaced binding, an entry
 * naming a command this build no longer has, a table that must degrade to the factory one.
 */
class ShortcutKeysTest {

    private val defaults = mapOf("PEN" to "B", "HIGHLIGHTER" to "H", "SAVE" to "Ctrl+S")

    @Test
    fun `a table survives its own encoding`() {
        val table = mapOf("PEN" to "Q", "HIGHLIGHTER" to "H", "SAVE" to "Ctrl+Shift+S")
        assertEquals(table, ShortcutKeys.decode(ShortcutKeys.encode(table)))
    }

    @Test
    fun `an empty key is dropped, and an unbind has its own word`() {
        // A command that lost its key to another must stay without one: the factory key coming back at the
        // next start would quietly undo the theft, so "none" is written rather than left unsaid.
        assertEquals("A:x", ShortcutKeys.encode(mapOf("A" to "x", "B" to "")))
        assertEquals("PEN:" + ShortcutKeys.UNBOUND, ShortcutKeys.encode(mapOf("PEN" to ShortcutKeys.UNBOUND)))
        val merged = ShortcutKeys.merge(defaults, mapOf("PEN" to ShortcutKeys.UNBOUND))
        assertNull("an unbind removes the row", merged["PEN"])
        assertEquals("the rows it did not name keep their keys", "H", merged["HIGHLIGHTER"])
        assertEquals(emptyMap<String, String>(), ShortcutKeys.decode(""))
        assertEquals(emptyMap<String, String>(), ShortcutKeys.decode(null))
        assertEquals(emptyMap<String, String>(), ShortcutKeys.decode("A:"))
    }

    @Test
    fun `a command this build does not know is dropped`() {
        val raw = "PEN:Q,REMOVED_TOOL:X"
        assertEquals(mapOf("PEN" to "Q"), ShortcutKeys.decode(raw, known = listOf("PEN", "SAVE")))
        assertEquals(
            "with no list, every id is kept (the backup reader's case)",
            mapOf("PEN" to "Q", "REMOVED_TOOL" to "X"),
            ShortcutKeys.decode(raw),
        )
    }

    @Test
    fun `binding a taken key moves it and names the command that lost it`() {
        val binding = ShortcutKeys.bind(defaults, command = "SAVE", key = "H")
        assertEquals("H", binding.key)
        assertEquals("the highlighter lost H", "HIGHLIGHTER", binding.displaced)
        assertEquals("H", binding.table["SAVE"])
        assertNull("and the loser has no key at all", binding.table["HIGHLIGHTER"])
        assertEquals("the untouched rows stay", "B", binding.table["PEN"])
    }

    @Test
    fun `a command's own old key is dropped when it moves`() {
        val binding = ShortcutKeys.bind(defaults, command = "PEN", key = "Q")
        assertNull("the pen does not keep B as well", binding.table["PEN"]?.takeIf { it == "B" })
        assertEquals("Q", binding.table["PEN"])
        assertNull("and nothing was displaced", binding.displaced)
        // Its old key is free again, so another command can take it.
        val next = ShortcutKeys.bind(binding.table, command = "HIGHLIGHTER", key = "B")
        assertEquals("B", next.table["HIGHLIGHTER"])
        assertNull(next.displaced)
    }

    @Test
    fun `a key that is already its own is not a conflict`() {
        val binding = ShortcutKeys.bind(defaults, command = "PEN", key = "B")
        assertNull(binding.displaced)
        assertEquals("B", binding.table["PEN"])
    }

    @Test
    fun `an empty key unbinds, and the key becomes free`() {
        val binding = ShortcutKeys.bind(defaults, command = "PEN", key = "")
        assertNull(binding.table["PEN"])
        assertEquals("H", binding.table["HIGHLIGHTER"])
        assertNull("nothing holds B now", ShortcutKeys.holder(binding.table, "B"))
    }

    @Test
    fun `the stored table is laid over the defaults, and the reset goes back to them`() {
        val stored = mapOf("PEN" to "Q")
        val merged = ShortcutKeys.merge(defaults, stored)
        assertEquals("Q", merged["PEN"])
        assertEquals("a command the file does not name keeps its factory key", "Ctrl+S", merged["SAVE"])
        assertEquals(
            "restore is the defaults, with nothing remembered",
            defaults,
            ShortcutKeys.restore(defaults),
        )
        assertNull("and an empty stored table is the defaults themselves", ShortcutKeys.decode("x")["PEN"])
    }

    @Test
    fun `a key is compared trimmed, never by its padding`() {
        assertEquals("Ctrl+S", ShortcutKeys.normalize(" Ctrl+S "))
        val binding = ShortcutKeys.bind(defaults, command = "EXPORT", key = " Ctrl+S ")
        assertEquals("SAVE", binding.displaced)
    }
}
