package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The circuit value keyboard carries the keys a component's value is actually written with — that is
 * its whole reason for existing next to the system IME (`ui/CircuitSymbolKeyboard.kt`).
 *
 * A layout that lost `Ω` would still compile and still accept typing, so the keys are pinned here:
 * the multiplier, the units, and the tolerance/range operators, without duplicates or blanks.
 */
class CircuitSymbolKeyboardTest {

    private val keys = CIRCUIT_SYMBOL_ROWS.flatten()

    @Test
    fun `the keyboard offers the symbols a component value is written with`() {
        val needed = listOf("k", "M", "m", "µ", "n", "p", "Ω", "V", "A", "W", "F", "H", "Hz", "±", "°C")
        for (key in needed) {
            assertTrue("\"$key\" is missing from the circuit keyboard", key in keys)
        }
    }

    @Test
    fun `no key is blank and none is repeated`() {
        assertTrue("a blank key would be a dead button", keys.all { it.isNotBlank() })
        assertEquals("a repeated key is two buttons doing one thing", keys.size, keys.toSet().size)
    }

    @Test
    fun `the keys are laid out in rows, not one long line`() {
        assertTrue("a keyboard with no rows has no layout", CIRCUIT_SYMBOL_ROWS.size >= 2)
        assertTrue("and every row has something in it", CIRCUIT_SYMBOL_ROWS.all { it.isNotEmpty() })
    }
}
