package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDestinationsTest {

    @Test
    fun `every SettingsSection maps to a SettingsDestination and every destination is reachable`() {
        val covered = SettingsSection.entries.map { it.toDestination() }.toSet()
        assertEquals(
            "Every dynamic SettingsDestination must be reachable from at least one SettingsSection",
            SettingsDestination.entries.toSet(),
            covered,
        )
    }

    @Test
    fun `every SettingsGroupCategory owns at least one destination`() {
        for (category in SettingsGroupCategory.entries) {
            assertTrue(
                "${category.title} must contain at least one destination",
                SettingsDestination.entries.any { it.category == category },
            )
        }
    }
}
