package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StemRailSectionTest {
    @Test fun hasTheFourOneTapEntries() {
        assertEquals(STEM_ENTRY_COUNT, STEM_ENTRIES.size)
        assertEquals(listOf("Function plot f(x)", "Geometry", "Table", "Circuit"), STEM_ENTRIES.map { it.label })
        assertNull(STEM_ENTRIES.first().tool)
    }

    @Test fun graphEntryIsNeverActiveAsATool() {
        assertFalse(stemEntryActive(STEM_ENTRIES[0], EditorTool.PEN))
    }

    @Test fun circuitEntryCoversItsWholeFamily() {
        val circuit = STEM_ENTRIES[3]
        assertTrue(stemEntryActive(circuit, EditorTool.CAPACITOR))
        assertFalse(stemEntryActive(circuit, EditorTool.PEN))
    }
}
