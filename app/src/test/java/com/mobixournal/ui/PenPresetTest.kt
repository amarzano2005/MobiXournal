package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PenPresetTest {

    @Test
    fun `default presets contain only Default`() {
        val defaults = PenPreset.DEFAULT_PRESETS
        assertEquals(1, defaults.size)
        val default = defaults.single()
        assertEquals("default", default.id)
        assertEquals("Default", default.name)
        assertEquals(PenPreset.FACTORY_DEFAULT_MIN_PRESSURE, default.minimumPressure, 0.001f)
        assertEquals(PenPreset.FACTORY_DEFAULT_MULTIPLIER, default.pressureMultiplier, 0.001f)
        assertEquals(0.05f, PenPreset.FACTORY_DEFAULT_MIN_PRESSURE, 0.001f)
        assertEquals(1.0f, PenPreset.FACTORY_DEFAULT_MULTIPLIER, 0.001f)
    }

    @Test
    fun `decode filters out retired extra default presets`() {
        val raw = "default;Default;0.05;1.0|light-touch;Light touch;0.08;1.5|custom;Custom;0.06;1.2"
        val decoded = decodePenPresets(raw)
        assertEquals(2, decoded.size)
        assertEquals("default", decoded[0].id)
        assertEquals("custom", decoded[1].id)
    }

    @Test
    fun `slugId produces valid kebab-case ids`() {
        assertEquals("soft-pencil", PenPreset.slugId("Soft Pencil"))
        assertEquals("my-preset-1", PenPreset.slugId("My Preset #1!"))
        assertEquals("preset", PenPreset.slugId("???"))
    }

    @Test
    fun `presets round-trip through encode and decode`() {
        val presets = listOf(
            PenPreset("def", "Default", 0.05f, 1.0f),
            PenPreset("light", "Light touch", 0.09f, 1.6f),
        )
        val encoded = encodePenPresets(presets)
        val decoded = decodePenPresets(encoded)
        assertEquals(presets, decoded)
    }

    @Test
    fun `decode handles null or malformed strings gracefully`() {
        val fallback = PenPreset.DEFAULT_PRESETS
        assertEquals(fallback, decodePenPresets(null))
        assertEquals(fallback, decodePenPresets(""))
        assertEquals(fallback, decodePenPresets("   "))
        assertEquals(fallback, decodePenPresets("invalid;format"))
    }

    @Test
    fun `decode ignores corrupted entries while preserving valid ones`() {
        val valid = PenPreset("p1", "Test", 0.05f, 1.0f)
        val raw = "bad;entry|${valid.id};${valid.name};${valid.minimumPressure};${valid.pressureMultiplier}"
        val decoded = decodePenPresets(raw)
        assertEquals(listOf(valid), decoded)
    }

    @Test
    fun `rename changes name of matching preset`() {
        val presets = listOf(
            PenPreset("p1", "Old Name", 0.05f, 1.0f),
            PenPreset("p2", "Other", 0.08f, 1.5f),
        )
        val renamed = renamePenPreset(presets, "p1", "New Name")
        assertEquals("New Name", renamed[0].name)
        assertEquals("Other", renamed[1].name)
    }

    @Test
    fun `addOrUpdate adds new preset or updates existing`() {
        val presets = listOf(PenPreset("p1", "P1", 0.05f, 1.0f))
        val newPreset = PenPreset("p2", "P2", 0.08f, 1.5f)
        val added = addOrUpdatePenPreset(presets, newPreset)
        assertEquals(2, added.size)

        val updatedP1 = PenPreset("p1", "P1 Updated", 0.06f, 1.2f)
        val updated = addOrUpdatePenPreset(added, updatedP1)
        assertEquals(2, updated.size)
        assertEquals("P1 Updated", updated[0].name)
        assertEquals(0.06f, updated[0].minimumPressure, 0.001f)
    }

    @Test
    fun `remove deletes preset but keeps at least one`() {
        val presets = listOf(
            PenPreset("p1", "P1", 0.05f, 1.0f),
            PenPreset("p2", "P2", 0.08f, 1.5f),
        )
        val afterRemove = removePenPreset(presets, "p1")
        assertEquals(listOf(presets[1]), afterRemove)

        // Deleting the last one keeps it so presets list is never empty
        val afterLastRemove = removePenPreset(afterRemove, "p2")
        assertEquals(afterRemove, afterLastRemove)
    }

    @Test
    fun `movePenPreset reorders presets correctly`() {
        val p1 = PenPreset("p1", "P1", 0.05f, 1.0f)
        val p2 = PenPreset("p2", "P2", 0.08f, 1.5f)
        val p3 = PenPreset("p3", "P3", 0.10f, 2.0f)
        val list = listOf(p1, p2, p3)

        // Move middle earlier (-1)
        val movedEarlier = movePenPreset(list, 1, -1)
        assertEquals(listOf(p2, p1, p3), movedEarlier)

        // Move first later (+1)
        val movedLater = movePenPreset(list, 0, 1)
        assertEquals(listOf(p2, p1, p3), movedLater)

        // Move middle to end (+1)
        val movedToEnd = movePenPreset(list, 1, 1)
        assertEquals(listOf(p1, p3, p2), movedToEnd)
    }

    @Test
    fun `movePenPreset with out of bounds index or delta is no-op`() {
        val p1 = PenPreset("p1", "P1", 0.05f, 1.0f)
        val p2 = PenPreset("p2", "P2", 0.08f, 1.5f)
        val list = listOf(p1, p2)

        // Moving index 0 earlier is out of range
        assertEquals(list, movePenPreset(list, 0, -1))

        // Moving last index later is out of range
        assertEquals(list, movePenPreset(list, 1, 1))

        // Negative index is out of range
        assertEquals(list, movePenPreset(list, -1, 1))

        // Index beyond size is out of range
        assertEquals(list, movePenPreset(list, 5, -1))
    }
}

