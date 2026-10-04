package com.mobixournal.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexPaletteTest {

    @Test
    fun `insertSnippetAtCursor inserts snippet at end when cursor is at end`() {
        val initial = TextFieldValue("x = ", TextRange(4))
        val updated = insertSnippetAtCursor(initial, "\\frac{a}{b}")
        assertEquals("x = \\frac{a}{b}", updated.text)
        assertEquals(4 + "\\frac{a}{b}".length, updated.selection.start)
    }

    @Test
    fun `insertSnippetAtCursor inserts in middle without erasing rest`() {
        val initial = TextFieldValue("y =  + 1", TextRange(4))
        val updated = insertSnippetAtCursor(initial, "\\sqrt{x}")
        assertEquals("y = \\sqrt{x} + 1", updated.text)
        assertEquals(4 + "\\sqrt{x}".length, updated.selection.start)
    }

    @Test
    fun `insertSnippetAtCursor replaces selected range`() {
        val initial = TextFieldValue("Hello placeholder World", TextRange(6, 17))
        val updated = insertSnippetAtCursor(initial, "\\alpha")
        assertEquals("Hello \\alpha World", updated.text)
        assertEquals(6 + "\\alpha".length, updated.selection.start)
    }

    @Test
    fun `all categories have symbols and non-empty snippets`() {
        for (cat in LatexCategory.entries) {
            val list = LATEX_PALETTE_SYMBOLS[cat]
            assertTrue("category $cat must have symbols", !list.isNullOrEmpty())
            for (sym in list!!) {
                assertTrue("label must not be blank", sym.label.isNotBlank())
                assertTrue("snippet must not be blank", sym.snippet.isNotBlank())
            }
        }
    }
}
