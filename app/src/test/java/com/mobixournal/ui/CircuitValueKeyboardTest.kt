package com.mobixournal.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The circuit value keyboard replaces the system IME, so it has to carry **everything a component's
 * value is made of** — digits, designator letters and the circuit symbols — and its editing model has
 * to be the ordinary one (insert at the cursor, backspace the selection or the character before it).
 *
 * Both halves are pure data and pure functions here (`ui/CircuitValueKeyboard.kt`), which is why they
 * can be pinned without a device: a layout that lost `Ω`, or a backspace that ate the wrong character,
 * would otherwise only be found by typing on a tablet.
 */
class CircuitValueKeyboardTest {

    private fun labels(page: CircuitKeyboardPage, lowercase: Boolean = false) =
        circuitKeyRows(page, lowercase).flatten().mapNotNull { it?.label }

    private fun field(text: String, caret: Int = text.length) = TextFieldValue(text, TextRange(caret))

    @Test
    fun `the digits page carries every digit and the separators a value uses`() {
        val keys = labels(CircuitKeyboardPage.DIGITS)
        for (digit in '0'..'9') assertTrue("$digit is missing", digit.toString() in keys)
        for (separator in listOf(".", ",", "-", "+")) assertTrue("$separator is missing", separator in keys)
        assertTrue("the backspace is on the page", CircuitKey.Backspace.label in keys)
    }

    @Test
    fun `the letters page carries the whole alphabet and switches case`() {
        val lower = labels(CircuitKeyboardPage.LETTERS, lowercase = true)
        for (letter in 'a'..'z') assertTrue("$letter is missing", letter.toString() in lower)
        val upper = labels(CircuitKeyboardPage.LETTERS, lowercase = false)
        assertTrue("a designator is written in capitals", "R" in upper)
        assertTrue("and lower case is one key away", "r" !in upper)
    }

    @Test
    fun `the symbols page carries multipliers, units and operators`() {
        val keys = labels(CircuitKeyboardPage.SYMBOLS)
        val needed = listOf("Ω", "µ", "k", "M", "G", "m", "n", "p", "V", "A", "W", "F", "H", "Hz", "°C")
        for (key in needed) assertTrue("\"$key\" is missing", key in keys)
        for (operator in listOf("±", "≈", "×", "·", "/", "⁻¹", "√", "%")) {
            assertTrue("\"$operator\" is missing", operator in keys)
        }
    }

    @Test
    fun `no key is blank`() {
        for (page in CircuitKeyboardPage.entries) {
            val rows = circuitKeyRows(page, lowercase = false)
            assertTrue("$page has no keys", rows.isNotEmpty())
            for (key in rows.flatten()) {
                if (key != null) assertTrue("$page has a blank key", key.label.isNotBlank())
            }
        }
    }

    @Test
    fun `a key types at the cursor, replacing a selection`() {
        val typed = applyCircuitKey(field("10 ", caret = 3), CircuitKey.Ch("k"))
        assertEquals("10 k", typed.text)
        assertEquals("the caret follows the key", 4, typed.selection.start)

        val replaced = applyCircuitKey(field("10 kΩ", caret = 0).copy(selection = TextRange(3, 5)), CircuitKey.Ch("MΩ"))
        assertEquals("10 MΩ", replaced.text)
        assertEquals(5, replaced.selection.start)
    }

    @Test
    fun `backspace deletes the selection, or the character behind the caret`() {
        val selection = applyCircuitKey(field("10 kΩ", caret = 0).copy(selection = TextRange(3, 5)), CircuitKey.Backspace)
        assertEquals("the selected characters go, nothing else", "10 ", selection.text)
        assertEquals(3, selection.selection.start)

        val oneChar = applyCircuitKey(field("10 k"), CircuitKey.Backspace)
        assertEquals("10 ", oneChar.text)
        assertEquals(3, oneChar.selection.start)

        val atStart = applyCircuitKey(field("10", caret = 0), CircuitKey.Backspace)
        assertEquals("nothing to delete", "10", atStart.text)
        assertEquals(0, atStart.selection.start)
    }

    @Test
    fun `the caret moves without touching the text and stops at the ends`() {
        val left = moveCircuitCaret(field("10 k", caret = 2), -1)
        assertEquals("10 k", left.text)
        assertEquals(1, left.selection.start)
        assertEquals("it stops at the start", 0, moveCircuitCaret(field("10", caret = 0), -1).selection.start)
        assertEquals("and at the end", 2, moveCircuitCaret(field("10"), 1).selection.start)
    }
}
